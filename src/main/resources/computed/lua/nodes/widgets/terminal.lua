local node = computed.node(1, "computed:terminal_widget", "Terminal")

node:category("widgets")
node:input("text", "string", { default = "" })
node:input("fg", "number", { default = 4294967295 })
node:input("bg", "number", { default = 4278190080 })
node:field("scale", "number", {
    default = 1, min = 0.5, max = 2, step = 0.5, label = "Text Scale"
})
node:field("cursor_x", "number", {
    default = -1, min = -1, step = 1, label = "Cursor X"
})
node:field("cursor_y", "number", {
    default = -1, min = -1, step = 1, label = "Cursor Y"
})
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
    default = 48, min = 1, label = "Height", step = 1,
    visible_when = { field = "layout_mode", equals = "manual" }
})
node:output("widget", "widget")
node:on_run(function(ctx)
    local rows = {}
    local text = ctx:input("text")
    if text ~= "" then
        for line in (text .. "\n"):gmatch("(.-)\n") do
            rows[#rows + 1] = line
        end
    end
    local inputs = ctx:inputs()
    for index = 1, 16 do
        local row = inputs["line_" .. index]
        if row ~= nil then
            rows[#rows + 1] = row
        end
    end
    local widget = ctx:endpoint("computed:widget"):call("terminal", rows,
        ctx:input("fg"), ctx:input("bg"),
        ctx:field("scale"),
        math.floor(ctx:field("cursor_x")), math.floor(ctx:field("cursor_y")))
    widget.x = math.floor(ctx:field("x"))
    widget.y = math.floor(ctx:field("y"))
    widget.width = math.max(1, math.floor(ctx:field("width")))
    widget.height = math.max(1, math.floor(ctx:field("height")))
    widget.layout_mode = ctx:field("layout_mode")
    widget.line = math.max(1, math.floor(ctx:field("line")))
    widget.span = math.max(1, math.floor(ctx:field("span")))
    widget.fit = ctx:field("fit")
    ctx:output("widget", widget)
end)

return node
