local node = computed.node(1, "computed:terminal_line", "Terminal Line")

node:category("widgets")
node:input("text", "string", { default = "" })
node:input("fg", "number", { default = 4294967295 })
node:input("bg", "number", { default = 4278190080 })
node:output("row", "table")
node:on_run(function(ctx)
    local row = ctx:endpoint("computed:widget"):call("line",
        ctx:input("text"), ctx:input("fg"), ctx:input("bg"))
    ctx:output("row", row)
end)

return node
