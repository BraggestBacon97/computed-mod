local node = computed.node(1, "computed:aeronautics_forward", "Forward Vector")

node:category("integration/aeronautics/telemetry/frame")
node:style("source")
node:execution("tick")
node:field("face", "direction", { default = "front", label = "Face" })
node:output('hx', 'number')
node:output('hy', 'number')
node:output('hz', 'number')
node:on_run(function(ctx)
    local data = ctx:endpoint("aeronautics:telemetry", ctx:field("face")):call("measure")
    ctx:output('hx', data.hx)
    ctx:output('hy', data.hy)
    ctx:output('hz', data.hz)
end)

return node
