local node = computed.node(1, "computed:aeronautics_pitch", "Pitch")

node:category("integration/aeronautics/telemetry/attitude")
node:style("source")
node:execution("tick")
node:field("face", "direction", { default = "front", label = "Face" })
node:output('pitch', 'number')
node:on_run(function(ctx)
    local data = ctx:endpoint("aeronautics:telemetry", ctx:field("face")):call("measure")
    ctx:output('pitch', data.pitch)
end)

return node
