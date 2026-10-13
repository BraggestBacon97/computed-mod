local node = computed.node(1, "computed:aeronautics_position", "Position")

node:category("integration/aeronautics/telemetry/position")
node:style("source")
node:execution("tick")
node:field("face", "direction", { default = "front", label = "Face" })
node:output('x', 'number')
node:output('y', 'number')
node:output('z', 'number')
node:on_run(function(ctx)
    local data = ctx:endpoint("aeronautics:telemetry", ctx:field("face")):call("measure")
    ctx:output('x', data.x)
    ctx:output('y', data.y)
    ctx:output('z', data.z)
end)

return node
