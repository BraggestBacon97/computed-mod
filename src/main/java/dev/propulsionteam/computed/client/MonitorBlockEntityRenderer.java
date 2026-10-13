package dev.propulsionteam.computed.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.propulsionteam.computed.client.renderer.MonitorWidgetPainter;
import dev.propulsionteam.computed.content.monitors.MonitorBlockEntity;
import dev.propulsionteam.computed.content.monitors.widgets.Widget;
import dev.propulsionteam.computed.network.ComputedNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;

/**
 * Draws the widget overlay across the inset screen surface of a formed monitor multiblock.
 *
 * <p>Widget coordinates are in pixels where {@link ComputedNetworking#SCREEN_PX_PER_BLOCK} pixels equal
 * one block. So the screen surface of a {@code 3×2} monitor occupies the rectangle
 * {@code (0, 0) .. (3*64, 2*64)} in widget space.
 */
public class MonitorBlockEntityRenderer implements BlockEntityRenderer<MonitorBlockEntity> {
    /** Push the background this far forward of the block face so it doesn't z-fight with the block. */
    private static final float BACKGROUND_FORWARD_OFFSET = 0.0020f;
    private static final float WIDGET_UNDERFILL_FORWARD_OFFSET = 0.0025f;
    private static final float WIDGET_FILL_FORWARD_OFFSET = 0.0030f;
    private static final float WIDGET_EDGE_FORWARD_OFFSET = 0.0040f;
    private static final float WIDGET_DETAIL_FORWARD_OFFSET = 0.0045f;
    private static final float WIDGET_TEXT_FORWARD_OFFSET = 0.0050f;

    public MonitorBlockEntityRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(MonitorBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource bufferSource,
                       int packedLight, int packedOverlay) {
        if (be.getXIndex() != 0 || be.getYIndex() != 0) return;

        int blocksW = be.getWidth();
        int blocksH = be.getHeight();
        Direction facing = be.getFront();
        Direction right = be.getRight();
        Direction gridDown = be.getDown();

        // Multiblock geometric center in the origin-block's local space:
        //   origin block center (0.5, 0.5, 0.5)
        //   + half the additional cells in the right axis
        //   + half the additional cells along the grid-down axis (visual up for wall mounts)
        //   + half a block out along the screen normal
        float cx = 0.5f
                + (blocksW - 1) * 0.5f * right.getStepX()
                + (blocksH - 1) * 0.5f * gridDown.getStepX()
                + 0.5f * facing.getStepX();
        float cy = 0.5f
                + (blocksW - 1) * 0.5f * right.getStepY()
                + (blocksH - 1) * 0.5f * gridDown.getStepY()
                + 0.5f * facing.getStepY();
        float cz = 0.5f
                + (blocksW - 1) * 0.5f * right.getStepZ()
                + (blocksH - 1) * 0.5f * gridDown.getStepZ()
                + 0.5f * facing.getStepZ();

        pose.pushPose();
        pose.translate(cx, cy, cz);

        // Rotation: map pose-local axes onto screen-space axes.
        //   pose +X → screenRight (right)
        //   pose +Y → screenDown  = -gridDown   (widget y grows visually downward)
        //   pose +Z → -facing     (into the screen)
        applyScreenRotation(pose, right, gridDown, facing);

        float inset = ComputedNetworking.BEZEL_MODEL_PIXELS / (float) ComputedNetworking.MODEL_PIXELS_PER_BLOCK;
        float usableW = Math.max(0.001f, blocksW - inset * 2f);
        float usableH = Math.max(0.001f, blocksH - inset * 2f);
        int logicalW = blocksW * ComputedNetworking.SCREEN_PX_PER_BLOCK;
        int logicalH = blocksH * ComputedNetworking.SCREEN_PX_PER_BLOCK;

        pose.translate(-blocksW / 2f + inset, -blocksH / 2f + inset, 0f);
        Matrix4f screenMat = new Matrix4f(pose.last().pose());
        VertexConsumer bgConsumer = bufferSource.getBuffer(RenderType.gui());
        fillBlockSpace(layer(screenMat, BACKGROUND_FORWARD_OFFSET), bgConsumer, 0, 0, usableW, usableH, 0xFF000000);

        Matrix4f widgetMat = new Matrix4f(screenMat).scale(usableW / logicalW, usableH / logicalH, 1f);
        Matrix4f underfillMat = layer(widgetMat, WIDGET_UNDERFILL_FORWARD_OFFSET);
        Matrix4f fillMat = layer(widgetMat, WIDGET_FILL_FORWARD_OFFSET);
        Matrix4f edgeMat = layer(widgetMat, WIDGET_EDGE_FORWARD_OFFSET);
        Matrix4f detailMat = layer(widgetMat, WIDGET_DETAIL_FORWARD_OFFSET);
        Matrix4f textMat = layer(widgetMat, WIDGET_TEXT_FORWARD_OFFSET);

        for (Widget w : be.getDrawList().widgets()) {
            MonitorWidgetPainter.renderWidget(w, underfillMat, fillMat, edgeMat, detailMat, textMat,
                    bufferSource, Minecraft.getInstance().font);
        }

        pose.popPose();
    }

    private static Matrix4f layer(Matrix4f mat, float forwardOffset) {
        return MonitorWidgetPainter.layer(mat, forwardOffset);
    }

    private static void applyScreenRotation(PoseStack pose, Direction right, Direction gridDown, Direction facing) {
        // Build a rotation matrix whose columns are the images of (1,0,0), (0,1,0), (0,0,1) in parent space.
        float rx = right.getStepX(),    ry = right.getStepY(),    rz = right.getStepZ();
        float dx = -gridDown.getStepX(), dy = -gridDown.getStepY(), dz = -gridDown.getStepZ();
        float fx = -facing.getStepX(),  fy = -facing.getStepY(),  fz = -facing.getStepZ();
        Matrix4f rot = new Matrix4f().set(new float[] {
                rx, ry, rz, 0f,   // col 0  → pose +X
                dx, dy, dz, 0f,   // col 1  → pose +Y
                fx, fy, fz, 0f,   // col 2  → pose +Z
                0f, 0f, 0f, 1f
        });
        pose.mulPose(rot);
    }

    /** Fills in block-space units (called for the background only). */
    private static void fillBlockSpace(Matrix4f mat, VertexConsumer vc, float x1, float y1, float x2, float y2, int argb) {
        emitQuad(mat, vc, x1, y1, x2, y2, argb);
    }

    private static void emitQuad(Matrix4f mat, VertexConsumer vc, float x1, float y1, float x2, float y2, int argb) {
        float a = ((argb >> 24) & 0xFF) / 255f;
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >>  8) & 0xFF) / 255f;
        float b = ((argb      ) & 0xFF) / 255f;
        if (a == 0f) return;
        vc.addVertex(mat, x1, y2, 0).setColor(r, g, b, a);
        vc.addVertex(mat, x2, y2, 0).setColor(r, g, b, a);
        vc.addVertex(mat, x2, y1, 0).setColor(r, g, b, a);
        vc.addVertex(mat, x1, y1, 0).setColor(r, g, b, a);
    }
}
