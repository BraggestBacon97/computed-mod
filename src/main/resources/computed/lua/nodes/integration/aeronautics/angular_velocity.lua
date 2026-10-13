local node = computed.node(1, "computed:aeronautics_angular_velocity", "Angular Velocity")

node:category("integration/aeronautics/telemetry/velocity")
node:style("source")
node:execution("tick")
node:field("face", "direction", { default = "front", label = "Face" })
node:output('wx', 'number')
node:output('wy', 'number')
node:output('wz', 'number')
node:on_run(function(ctx)
    local data = ctx:endpoint("aeronautics:telemetry", ctx:field("face")):call("measure")
    ctx:output('wx', data.wx)
    ctx:output('wy', data.wy)
    ctx:output('wz', data.wz)
end)

return node
