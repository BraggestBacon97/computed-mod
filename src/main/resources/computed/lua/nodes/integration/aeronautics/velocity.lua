local node = computed.node(1, "computed:aeronautics_velocity", "Velocity")

node:category("integration/aeronautics/telemetry/velocity")
node:style("source")
node:execution("tick")
node:field("face", "direction", { default = "front", label = "Face" })
node:output('vx', 'number')
node:output('vy', 'number')
node:output('vz', 'number')
node:on_run(function(ctx)
    local data = ctx:endpoint("aeronautics:telemetry", ctx:field("face")):call("measure")
    ctx:output('vx', data.vx)
    ctx:output('vy', data.vy)
    ctx:output('vz', data.vz)
end)

return node
