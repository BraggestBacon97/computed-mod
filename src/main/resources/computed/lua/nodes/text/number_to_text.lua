local node = computed.node(1, "computed:number_to_text", "Number to Text")

node:category("text")
node:input("value", "number", { default = 0 })
node:field("format", "text", { default = "%.1f", label = "Format" })
node:output("text", "string")
node:on_run(function(ctx)
    local format = ctx:field("format")
    if format == nil or format == "" then
        format = "%.1f"
    end
    ctx:output("text", string.format(format, ctx:input("value")))
end)

return node
