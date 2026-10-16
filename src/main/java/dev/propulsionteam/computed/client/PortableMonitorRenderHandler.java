package dev.propulsionteam.computed.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.propulsionteam.computed.Computed;
import dev.propulsionteam.computed.content.items.PortableMonitorItem;
import dev.propulsionteam.computed.content.items.PortableMonitorRaiseBridge;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Renders the real first-person player arm while the portable monitor is raised, driven by the
 * {@code RightArm} keyframe track of {@code animation.portable_monitor.open}. Vanilla renders no
 * arm for regular held items, so this handler adds it back; the item model itself keeps being
 * rendered normally by vanilla/GeckoLib. The handler never cancels {@link RenderHandEvent}.
 */
@EventBusSubscriber(modid = Computed.MODID, value = Dist.CLIENT)
public final class PortableMonitorRenderHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(PortableMonitorRenderHandler.class);

    /** Open animation length in seconds (from the animation JSON). */
    private static final float ANIM_SECONDS = 0.5417F;
    /** Same length in ticks; keep in sync with {@code PortableMonitorRaise.RAISE_TICKS}. */
    private static final int ANIM_TICKS = 11;

    private static final ResourceLocation ANIMATIONS = ResourceLocation.fromNamespaceAndPath(
            Computed.MODID, "animations/item/portable_monitor.animation.json");

    // --- Tunable grip/pivot offsets: adjust in-game if the arm sits badly ---
    /** Fine-tune: hand offset from the vanilla item-hold position. */
    private static final float GRIP_X = 0.05F;
    private static final float GRIP_Y = -0.05F;
    private static final float GRIP_Z = 0.0F;
    /** Wrist/elbow pivot around which the animated RightArm rotation is applied. */
    private static final float PIVOT_X = 0.0F;
    private static final float PIVOT_Y = -0.10F;
    private static final float PIVOT_Z = 0.10F;

    /** Animation clock: -1 = fully lowered (hidden); >=0 = progress in the current transition. */
    private static int animTicks = -1;
    private static boolean opening = true;
    private static boolean wasRaised = false;

    /** Lazily parsed RightArm keyframes of the open animation; null until the first attempt. */
    private static Keyframe[] rightArmKeys;

    private PortableMonitorRenderHandler() {}

    private record Keyframe(float time, float x, float y, float z) {}

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            animTicks = -1;
            wasRaised = false;
            return;
        }
        boolean raised = PortableMonitorRaiseBridge.isRaised();
        if (raised && !wasRaised) {
            opening = true;
            animTicks = 0;
        } else if (!raised && wasRaised) {
            opening = false;
            animTicks = 0;
        } else if (animTicks >= 0) {
            animTicks++;
            if (animTicks > ANIM_TICKS) {
                if (opening) {
                    animTicks = ANIM_TICKS; // hold at end while the screen stays open
                } else {
                    animTicks = -1; // lowering finished: hide the arm again
                }
            }
        }
        wasRaised = raised;
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || !(stack.getItem() instanceof PortableMonitorItem)) return;
        if (animTicks < 0) return; // fully lowered: vanilla shows just the item

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || player.isInvisible()) return;

        // Sample the open animation's RightArm track; closing plays the same track backwards,
        // which matches the derived close animation in the animation JSON.
        float seconds = (animTicks + event.getPartialTick()) / 20.0F;
        float t = Mth.clamp(opening ? seconds : ANIM_SECONDS - seconds, 0.0F, ANIM_SECONDS);
        float[] rot = sampleRightArm(t);

        PlayerRenderer renderer =
                (PlayerRenderer) mc.getEntityRenderDispatcher().getRenderer(player);
        if (renderer == null) return;

        PoseStack pose = event.getPoseStack();
        float equip = event.getEquipProgress();

        pose.pushPose();
        // Position: same hold spot as the item (applyItemArmTransform) + grip tuning.
        pose.translate(0.56F, -0.52F + equip * -0.6F, -0.72F);
        pose.translate(GRIP_X, GRIP_Y, GRIP_Z);
        // Animated rotation around the wrist pivot, ZYX like GeckoLib.
        pose.translate(PIVOT_X, PIVOT_Y, PIVOT_Z);
        pose.mulPose(Axis.ZP.rotationDegrees(rot[2]));
        pose.mulPose(Axis.YP.rotationDegrees(rot[1]));
        pose.mulPose(Axis.XP.rotationDegrees(rot[0]));
        pose.translate(-PIVOT_X, -PIVOT_Y, -PIVOT_Z);
        // Orient the vanilla arm model into this space (renderPlayerArm tail, right arm, no swing).
        pose.mulPose(Axis.YP.rotationDegrees(45.0F));
        pose.translate(-1.0F, 3.6F, 3.5F);
        pose.mulPose(Axis.ZP.rotationDegrees(120.0F));
        pose.mulPose(Axis.XP.rotationDegrees(200.0F));
        pose.mulPose(Axis.YP.rotationDegrees(-135.0F));
        pose.translate(5.6F, 0.0F, 0.0F);

        MultiBufferSource buffers = event.getMultiBufferSource();
        renderer.renderRightHand(pose, buffers, event.getPackedLight(), player);
        pose.popPose();
    }

    private static Keyframe[] rightArmKeys() {
        Keyframe[] keys = rightArmKeys;
        if (keys != null) return keys;
        keys = new Keyframe[0];
        try {
            var resource =
                    Minecraft.getInstance().getResourceManager().getResource(ANIMATIONS).orElse(null);
            if (resource != null) {
                try (var reader =
                        new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
                    JsonObject rotation = JsonParser.parseReader(reader)
                            .getAsJsonObject()
                            .getAsJsonObject("animations")
                            .getAsJsonObject("animation.portable_monitor.open")
                            .getAsJsonObject("bones")
                            .getAsJsonObject("RightArm")
                            .getAsJsonObject("rotation");
                    List<Keyframe> list = new ArrayList<>();
                    for (var entry : rotation.entrySet()) {
                        float time = Float.parseFloat(entry.getKey());
                        float[] v = readVec(entry.getValue());
                        list.add(new Keyframe(time, v[0], v[1], v[2]));
                    }
                    list.sort(Comparator.comparingDouble(Keyframe::time));
                    keys = list.toArray(new Keyframe[0]);
                }
            } else {
                LOGGER.warn("Portable monitor animation resource missing: {}", ANIMATIONS);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to load portable monitor RightArm keyframes", e);
        }
        rightArmKeys = keys;
        return keys;
    }

    /** Reads a keyframe vector, supporting both {@code [x,y,z]} and {@code {"vector":[x,y,z]}}. */
    private static float[] readVec(com.google.gson.JsonElement element) {
        JsonArray a = element.isJsonArray()
                ? element.getAsJsonArray()
                : element.getAsJsonObject().getAsJsonArray("vector");
        return new float[] {a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
    }

    private static float[] sampleRightArm(float t) {
        Keyframe[] keys = rightArmKeys();
        if (keys.length == 0) return new float[] {0.0F, 0.0F, 0.0F};
        if (t <= keys[0].time()) return new float[] {keys[0].x(), keys[0].y(), keys[0].z()};
        Keyframe last = keys[keys.length - 1];
        if (t >= last.time()) return new float[] {last.x(), last.y(), last.z()};
        for (int i = 0; i < keys.length - 1; i++) {
            Keyframe a = keys[i];
            Keyframe b = keys[i + 1];
            if (t >= a.time() && t <= b.time()) {
                float span = b.time() - a.time();
                float f = span <= 0.0F ? 0.0F : (t - a.time()) / span;
                return new float[] {
                    Mth.lerp(f, a.x(), b.x()),
                    Mth.lerp(f, a.y(), b.y()),
                    Mth.lerp(f, a.z(), b.z())
                };
            }
        }
        return new float[] {0.0F, 0.0F, 0.0F};
    }
}
