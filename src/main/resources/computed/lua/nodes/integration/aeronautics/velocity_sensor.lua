local node = computed.node(1, "computed:aeronautics_velocity_sensor", "Velocity Sensor")

node:category("integration/aeronautics/telemetry/velocity")
node:style("source")
node:execution("tick")
node:field("face", "direction", { default = "front" })
node:field("announce_interval", "number", { default = 40 })
node:output("speed", "number")
node:output("sensor", "number")
node:output("delta", "number")
node:state("announced", 0)
node:on_run(function(ctx)
    local data = ctx:endpoint("aeronautics:telemetry", ctx:field("face")):call("measure")

    local vx = data.vx or 0
    local vy = data.vy or 0
    local vz = data.vz or 0
    local hx = data.hx or 0
    local hy = data.hy or 0
    local hz = data.hz or 0

    local speed = data.speed
    if speed == nil then
        speed = math.sqrt(vx * vx + vy * vy + vz * vz)
    end
    local sensor = math.abs(vx * hx + vy * hy + vz * hz)
    local delta = speed - sensor

    ctx:output("speed", speed)
    ctx:output("sensor", sensor)
    ctx:output("delta", delta)

    local interval = ctx:field("announce_interval")
    if interval and interval > 0 then
        local time = ctx:endpoint("computed:world"):call("time")
        if time >= ctx:state("announced") + interval then
            ctx:set_state("announced", time)
            local ok = pcall(function()
                ctx:endpoint("computed:command"):call(
                    "run",
                    "say [velocity] speed="
                        .. string.format("%.2f", speed)
                        .. " sensor="
                        .. string.format("%.2f", sensor)
                        .. " delta="
                        .. string.format("%+.2f", delta))
            end)
            if not ok then
                -- Run Command is creative-only: announcing is best-effort, the outputs still work
            end
        end
    end
end)

return node
