local node = computed.node(1, "computed:aeronautics_heading", "Heading")

node:category("integration/aeronautics/telemetry/attitude")
node:style("source")
node:execution("tick")
node:field("face", "direction", { default = "front", label = "Face" })
node:output('heading', 'number')
node:on_run(function(ctx)
    local data = ctx:endpoint("aeronautics:telemetry", ctx:field("face")):call("measure")
    ctx:output('heading', data.heading)
end)

return node
