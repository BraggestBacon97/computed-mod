local node = computed.node(1, "computed:speaker_stop", "Speaker Stop")

node:category("speaker")
node:style("sink")
node:execution("input")
node:field("face", "direction", { default = "front", label = "Face" })
node:input("trigger", "boolean", { default = false })
node:input("event", "event", { required = false, default = 0 })
node:on_run(function(ctx)
    local active = ctx:input("trigger") or ctx:input("event") ~= 0
    if active then
        ctx:endpoint("computed:speaker", ctx:field("face")):call("stop")
    end
end)

return node
