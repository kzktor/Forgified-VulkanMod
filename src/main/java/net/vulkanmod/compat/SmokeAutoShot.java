package net.vulkanmod.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.vulkanmod.Initializer;
import net.vulkanmod.render.chunk.WorldRenderer;

public final class SmokeAutoShot {
    private static int ticks = 0;
    private static boolean done = false;

    private SmokeAutoShot() {}

    public static void registerIfEnabled() {
        if (!Boolean.getBoolean("vulkanmod.smoke.autoshot")) {
            return;
        }

        Initializer.LOGGER.info("[DIAG] SmokeAutoShot enabled");
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            Minecraft mc = Minecraft.getInstance();
            if (done || mc.level == null || mc.player == null) {
                return;
            }

            ticks++;
            if (ticks == 100 || ticks == 160 || ticks == 220) {
                WorldRenderer worldRenderer = WorldRenderer.getInstance();
                if (worldRenderer != null) {
                    Initializer.LOGGER.info("[DIAG] chunks: {}", worldRenderer.getChunkStatistics());
                }

                Screenshot.grab(mc.gameDirectory, "diag_auto_" + ticks + ".png",
                        mc.getMainRenderTarget(), component ->
                                Initializer.LOGGER.info("[DIAG] screenshot: {}", component.getString()));
            }

            if (ticks >= 280) {
                done = true;
                Initializer.LOGGER.info("[DIAG] SmokeAutoShot complete, stopping client");
                mc.stop();
            }
        });
    }
}
