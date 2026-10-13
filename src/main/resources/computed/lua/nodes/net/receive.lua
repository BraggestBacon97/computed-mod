local node = computed.node(1, "computed:net_receive", "Receive Message")

node:category("net")
node:execution("tick")
node:input("trigger", "boolean", { default = false })
node:input("event", "event", { required = false, default = 0 })
node:field("timeout", "number", { default = 0 })
node:output("received", "boolean")
node:output("sender", "number")
node:output("message", "string")
node:on_run(function(ctx)
    if not (ctx:input("trigger") or ctx:input("event") ~= 0) then
        return
    end
    local net = ctx:endpoint("computed:net")
    local sender, message = net:call("receive", ctx:field("timeout"))
    ctx:output("received", message ~= nil)
    ctx:output("sender", sender or 0)
    ctx:output("message", message or "")
end)

return node
