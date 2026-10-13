local node = computed.node(1, "computed:aeronautics_source", "Source")

node:category("integration/aeronautics/telemetry/presence")
node:style("source")
node:execution("tick")
node:field("face", "direction", { default = "front", label = "Face" })
node:output('source', 'string')
node:on_run(function(ctx)
    local data = ctx:endpoint("aeronautics:telemetry", ctx:field("face")):call("measure")
    ctx:output('source', data.source)
end)

return node
