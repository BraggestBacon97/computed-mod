local node = computed.node(1, "computed:net_send", "Send Message")

node:category("net")
node:style("sink")
node:input("trigger", "boolean", { default = false })
node:input("event", "event", { required = false, default = 0 })
node:input("message", "string", { default = "" })
node:field("target", "number", { default = 0 })
node:on_run(function(ctx)
    if ctx:input("trigger") or ctx:input("event") ~= 0 then
        local net = ctx:endpoint("computed:net")
        net:call("send", ctx:field("target"), ctx:input("message"))
    end
end)

return node
