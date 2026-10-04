package com.dranant.client.renderer;

import com.dranant.block.entity.ItemDisplayPedestalBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/**
 * ProBoiz-style 360-degree floating display: the item hovers 0.6 blocks above the pedestal, spins smoothly on the Y axis
 * at (gameTime + partialTick) * 3 degrees, bobs on a sine wave, and shows a "[Item] - Cost: X Diamonds" hologram.
 */
public class ItemDisplayPedestalRenderer implements BlockEntityRenderer<ItemDisplayPedestalBlockEntity> {
    private static final double PEDESTAL_TOP = 1.0D;
    private static final double HOVER = 0.6D;

    private final ItemRenderer itemRenderer;
    private final Font font;

    public ItemDisplayPedestalRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
        this.font = context.getFont();
    }

    @Override
    public void render(ItemDisplayPedestalBlockEntity pedestal, float partialTick, PoseStack poseStack, MultiBufferSource buffer,
                       int packedLight, int packedOverlay) {
        ItemStack stack = pedestal.getDisplayedItem();
        if (stack.isEmpty()) return;
        Level level = pedestal.getLevel();
        float time = (level != null ? level.getGameTime() : 0L) + partialTick;
        float rotation = (time * 3.0F) % 360.0F;
        float bob = Mth.sin(time * 0.1F) * 0.08F;
        int light = level != null ? LevelRenderer.getLightColor(level, pedestal.getBlockPos().above()) : LightTexture.FULL_BRIGHT;

        // floating, spinning item
        poseStack.pushPose();
        poseStack.translate(0.5D, PEDESTAL_TOP + HOVER + bob, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(rotation));
        poseStack.scale(0.75F, 0.75F, 0.75F);
        itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, poseStack, buffer, level,
                (int) pedestal.getBlockPos().asLong());
        poseStack.popPose();

        // nameplate hologram (only within 16 blocks)
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.distanceToSqr(pedestal.getBlockPos().getCenter()) > 16.0D * 16.0D) return;
        Component text = Component.empty()
                .append(stack.getHoverName().copy().withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" - Cost: " + pedestal.getDiamondPrice() + " Diamonds").withStyle(ChatFormatting.GOLD));
        poseStack.pushPose();
        poseStack.translate(0.5D, PEDESTAL_TOP + HOVER + 0.65D + bob * 0.5F, 0.5D);
        poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(0.02F, -0.02F, 0.02F);
        Matrix4f matrix = poseStack.last().pose();
        float x = -font.width(text) / 2.0F;
        int background = (int) (mc.options.getBackgroundOpacity(0.35F) * 255.0F) << 24;
        font.drawInBatch(text, x, 0.0F, 0xFFFFFFFF, false, matrix, buffer, Font.DisplayMode.NORMAL, background, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }

    /** Lets the floating item and hologram render even when only the area above the pedestal is on screen. */
    public AABB getRenderBoundingBox(ItemDisplayPedestalBlockEntity pedestal) {
        return new AABB(pedestal.getBlockPos()).expandTowards(0.0D, 2.5D, 0.0D).inflate(1.0D, 0.0D, 1.0D);
    }
}
