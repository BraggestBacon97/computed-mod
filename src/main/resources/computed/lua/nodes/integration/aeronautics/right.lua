local node = computed.node(1, "computed:aeronautics_right", "Right Vector")

node:category("integration/aeronautics/telemetry/frame")
node:style("source")
node:execution("tick")
node:field("face", "direction", { default = "front", label = "Face" })
node:output('rx', 'number')
node:output('ry', 'number')
node:output('rz', 'number')
node:on_run(function(ctx)
    local data = ctx:endpoint("aeronautics:telemetry", ctx:field("face")):call("measure")
    ctx:output('rx', data.rx)
    ctx:output('ry', data.ry)
    ctx:output('rz', data.rz)
end)

return node
