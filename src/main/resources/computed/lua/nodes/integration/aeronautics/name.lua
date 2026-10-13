local node = computed.node(1, "computed:aeronautics_name", "Name")

node:category("integration/aeronautics/telemetry/presence")
node:style("source")
node:execution("tick")
node:field("face", "direction", { default = "front", label = "Face" })
node:output('name', 'string')
node:on_run(function(ctx)
    local data = ctx:endpoint("aeronautics:telemetry", ctx:field("face")):call("measure")
    ctx:output('name', data.name)
end)

return node
