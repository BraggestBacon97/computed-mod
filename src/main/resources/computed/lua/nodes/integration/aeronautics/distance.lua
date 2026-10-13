local node = computed.node(1, "computed:aeronautics_distance", "Distance")

node:category("integration/aeronautics/telemetry/target")
node:style("source")
node:execution("tick")
node:field("face", "direction", { default = "front", label = "Face" })
node:output('distance', 'number')
node:on_run(function(ctx)
    local data = ctx:endpoint("aeronautics:telemetry", ctx:field("face")):call("measure")
    ctx:output('distance', data.distance)
end)

return node
