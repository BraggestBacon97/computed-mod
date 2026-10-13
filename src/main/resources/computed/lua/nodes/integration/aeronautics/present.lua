local node = computed.node(1, "computed:aeronautics_present", "Present")

node:category("integration/aeronautics/telemetry/presence")
node:style("source")
node:execution("tick")
node:field("face", "direction", { default = "front", label = "Face" })
node:output('present', 'boolean')
node:on_run(function(ctx)
    local data = ctx:endpoint("aeronautics:telemetry", ctx:field("face")):call("measure")
    ctx:output('present', data.present)
end)

return node
