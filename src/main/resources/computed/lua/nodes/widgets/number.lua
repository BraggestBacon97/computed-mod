local node = computed.node(1, "computed:number_widget", "Number Widget")

node:category("widgets")
node:input("value", "number", { default = 0 })
node:input("color", "number", { default = 4294967295 })
node:field("format", "text", { default = "%.4g", label = "Format" })
node:field("layout_mode", "choice", {
    default = "line",
    label = "Layout",
    choices = { "line", "manual" }
})
node:field("line", "number", {
    default = 1, min = 1, step = 1, label = "Line",
    visible_when = { field = "layout_mode", equals = "line" }
})
node:field("span", "number", {
    default = 1, min = 1, step = 1, label = "Line Span",
    visible_when = { field = "layout_mode", equals = "line" }
})
node:field("fit", "choice", {
    default = "auto",
    label = "Fit",
    choices = { "auto", "fill" },
    visible_when = { field = "layout_mode", equals = "line" }
})
node:field("x", "number", {
    default = 0, label = "X",
    visible_when = { field = "layout_mode", equals = "manual" }
})
node:field("y", "number", {
    default = 0, label = "Y",
    visible_when = { field = "layout_mode", equals = "manual" }
})
node:field("width", "number", {
    default = 64, min = 1, label = "Width", step = 1,
    visible_when = { field = "layout_mode", equals = "manual" }
})
node:field("height", "number", {
    default = 12, min = 1, label = "Height", step = 1,
    visible_when = { field = "layout_mode", equals = "manual" }
})
node:field("alignment", "choice", {
    default = "right",
    label = "Alignment",
    choices = { "left", "center", "right" }
})
node:output("widget", "widget")
node:on_run(function(ctx)
    local value = ctx:input("value")
    local format = ctx:field("format")
    if format == nil or format == "" then
        format = "%.4g"
    end
    -- The Lua runtime ignores printf precision (%.4g renders Java-style as
    -- "18000.0" or "4.294967295E9"), so integral values (colours, ticks, time,
    -- positions) render via tostring, which is exact for them. An explicitly
    -- chosen format always wins, even for integral values.
    local text
    if format == "%.4g" and value % 1 == 0 and math.abs(value) < 9007199254740992 then
        text = tostring(value)
    else
        text = string.format(format, value)
    end
    local widgets = ctx:endpoint("computed:widget")
    local widget = widgets:call("number", value, text,
        ctx:input("color"))
    widget.x = math.floor(ctx:field("x"))
    widget.y = math.floor(ctx:field("y"))
    widget.width = math.max(1, math.floor(ctx:field("width")))
    widget.height = math.max(1, math.floor(ctx:field("height")))
    widget.alignment = ctx:field("alignment")
    widget.layout_mode = ctx:field("layout_mode")
    widget.line = math.max(1, math.floor(ctx:field("line")))
    widget.span = math.max(1, math.floor(ctx:field("span")))
    widget.fit = ctx:field("fit")
    ctx:output("widget", widget)
end)

return node
