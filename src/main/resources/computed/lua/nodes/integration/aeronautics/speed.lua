local node = computed.node(1, "computed:aeronautics_speed", "Speed")

node:category("integration/aeronautics/telemetry/velocity")
node:style("source")
node:execution("tick")
node:field("face", "direction", { default = "front", label = "Face" })
node:output('speed', 'number')
node:on_run(function(ctx)
    local data = ctx:endpoint("aeronautics:telemetry", ctx:field("face")):call("measure")
    ctx:output('speed', data.speed)
end)

return node
