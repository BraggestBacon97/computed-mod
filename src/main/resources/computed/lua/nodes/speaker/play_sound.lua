local node = computed.node(1, "computed:speaker_play_sound", "Speaker Play Sound")

node:category("speaker")
node:style("sink")
node:execution("input")
node:field("face", "direction", { default = "front", label = "Face" })
node:field("sound", "text", { default = "block.note_block.pling", label = "Sound ID" })
node:field("volume", "number", { default = 1.0, min = 0.0, max = 4.0, step = 0.1, label = "Volume" })
node:field("pitch", "number", { default = 1.0, min = 0.5, max = 2.0, step = 0.05, label = "Pitch" })
node:input("trigger", "boolean", { default = false })
node:input("event", "event", { required = false, default = 0 })
node:on_run(function(ctx)
    local active = ctx:input("trigger") or ctx:input("event") ~= 0
    if active then
        ctx:endpoint("computed:speaker", ctx:field("face")):call(
            "play_sound",
            ctx:field("sound"),
            ctx:field("volume"),
            ctx:field("pitch"))
    end
end)

return node
