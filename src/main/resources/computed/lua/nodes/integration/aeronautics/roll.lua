local node = computed.node(1, "computed:aeronautics_roll", "Roll")

node:category("integration/aeronautics/telemetry/attitude")
node:style("source")
node:execution("tick")
node:field("face", "direction", { default = "front", label = "Face" })
node:output('roll', 'number')
node:on_run(function(ctx)
    local data = ctx:endpoint("aeronautics:telemetry", ctx:field("face")):call("measure")
    ctx:output('roll', data.roll)
end)

return node
