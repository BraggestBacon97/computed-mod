local node = computed.node(1, "computed:speaker_play_note", "Speaker Play Note")

node:category("speaker")
node:style("sink")
node:execution("input")
node:field("face", "direction", { default = "front", label = "Face" })
node:field("instrument", "choice", {
    default = "harp",
    label = "Instrument",
    choices = {
        "harp", "basedrum", "snare", "hat", "bass", "flute", "bell", "guitar",
        "chime", "xylophone", "iron_xylophone", "cow_bell", "didgeridoo",
        "bit", "banjo", "pling"
    }
})
node:field("note", "number", { default = 12, min = 0, max = 24, step = 1, label = "Note (0-24)" })
node:input("trigger", "boolean", { default = false })
node:input("event", "event", { required = false, default = 0 })
node:on_run(function(ctx)
    local active = ctx:input("trigger") or ctx:input("event") ~= 0
    if active then
        ctx:endpoint("computed:speaker", ctx:field("face")):call(
            "play_note",
            ctx:field("instrument"),
            ctx:field("note"))
    end
end)

return node
