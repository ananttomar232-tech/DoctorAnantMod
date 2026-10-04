package com.dranant.client;

import com.dranant.counter.ClientDiamondData;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.util.Mth;
import net.minecraft.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Top-right HUD: [diamond icon] Diamonds: N with animated "+X [diamond]" popups that float upward and fade.
 * (Minecraft's font has no emoji glyph for the diamond, so the real diamond item icon is drawn in its place.)
 */
public class DiamondTrackerOverlay implements LayeredDraw.Layer {
    private static final ItemStack DIAMOND = new ItemStack(Items.DIAMOND);

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || !ClientDiamondData.isVisible()) return;
        Font font = mc.font;
        boolean running = ClientDiamondData.isRunning();
        String label = "Diamonds: " + ClientDiamondData.getTotal();
        int textWidth = font.width(label);
        int right = graphics.guiWidth() - 6;
        int panelLeft = right - textWidth - 26;
        int top = 6;

        graphics.fill(panelLeft, top, right, top + 20, 0x90000000);
        graphics.fill(panelLeft, top + 19, right, top + 20, running ? 0xFF2EE6E6 : 0xFF777777);
        graphics.renderItem(DIAMOND, panelLeft + 3, top + 2);
        graphics.drawString(font, label, panelLeft + 22, top + 6, running ? 0xFF55FFFF : 0xFFAAAAAA, true);
        if (!running) {
            graphics.drawString(font, "(paused)", right - font.width("(paused)"), top + 23, 0xFFAAAAAA, true);
        }

        long now = Util.getMillis();
        List<ClientDiamondData.Popup> popups = ClientDiamondData.getActivePopups();
        for (int i = 0; i < popups.size(); i++) {
            ClientDiamondData.Popup popup = popups.get(i);
            float t = Mth.clamp((now - popup.startMillis()) / (float) ClientDiamondData.POPUP_MILLIS, 0.0F, 1.0F);
            int alpha = Mth.clamp((int) ((1.0F - t) * 255.0F), 8, 255);
            float scale = 1.0F + 0.35F * (1.0F - t);
            String text = "+" + popup.amount();
            float px = panelLeft + 10 + popup.xJitter();
            float py = top + 40 - t * 26.0F + i * 2;
            graphics.pose().pushPose();
            graphics.pose().translate(px, py, 200.0F);
            graphics.pose().scale(scale, scale, 1.0F);
            graphics.drawString(font, text, 0, 0, (alpha << 24) | 0x55FF55, true);
            graphics.pose().pushPose();
            graphics.pose().translate(font.width(text) + 1.0F, -3.0F, 0.0F);
            graphics.pose().scale(0.75F, 0.75F, 1.0F);
            graphics.renderItem(DIAMOND, 0, 0);
            graphics.pose().popPose();
            graphics.pose().popPose();
        }
    }
}
