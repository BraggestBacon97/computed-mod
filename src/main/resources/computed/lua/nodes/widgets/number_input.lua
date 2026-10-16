local node = computed.node(1, "computed:number_input_widget", "Number Input Widget")

node:category("widgets")
node:input("placeholder", "string", { default = "0" })
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
    default = 16, min = 1, label = "Height", step = 1,
    visible_when = { field = "layout_mode", equals = "manual" }
})
node:output("widget", "widget")
node:output("value", "number")
node:output("submitted", "boolean")
node:state("value", 0)
node:state("pending_submit", false)
local function format_value(value, format)
    if format == nil or format == "" then
        format = "%.4g"
    end
    -- Same rule as the number widget: the runtime ignores printf precision, so
    -- integral values render exactly and an explicit format always wins.
    if format == "%.4g" and value % 1 == 0 and math.abs(value) < 9007199254740992 then
        return tostring(value)
    end
    return string.format(format, value)
end
local function render(ctx)
    local value = ctx:state("value")
    local widget = ctx:endpoint("computed:widget"):call(
        "text_input",
        ctx:input("placeholder"),
        ctx:input("color"),
        format_value(value, ctx:field("format")))
    widget.x = math.floor(ctx:field("x"))
    widget.y = math.floor(ctx:field("y"))
    widget.width = math.max(1, math.floor(ctx:field("width")))
    widget.height = math.max(1, math.floor(ctx:field("height")))
    widget.layout_mode = ctx:field("layout_mode")
    widget.line = math.max(1, math.floor(ctx:field("line")))
    widget.span = math.max(1, math.floor(ctx:field("span")))
    widget.fit = ctx:field("fit")
    ctx:output("widget", widget)
    ctx:output("value", value)
    ctx:output("submitted", ctx:state("pending_submit"))
    ctx:set_state("pending_submit", false)
end
node:on_run(render)
node:on_event("input", function(ctx, text)
    local number = tonumber(text)
    if number ~= nil then
        ctx:set_state("value", number)
        ctx:set_state("pending_submit", true)
    end
    render(ctx)
end)

return node
