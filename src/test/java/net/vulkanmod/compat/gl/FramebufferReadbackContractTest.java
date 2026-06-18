package net.vulkanmod.compat.gl;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FramebufferReadbackContractTest {
    @Test
    void readPixelsFallbackIsReportedAsFramebufferReadbackContractGap() throws Exception {
        String source = Files.readString(Path.of("src/main/java/net/vulkanmod/mixin/compatibility/gl/GL11M.java"));

        assertTrue(source.contains("GlEmulationLog.warnContractGap(\"framebuffer_readback\", \"glReadPixels\""));
    }

    @Test
    void framebufferBlitFallbackIsReportedAsFramebufferReadbackContractGap() throws Exception {
        String source = Files.readString(Path.of("src/main/java/net/vulkanmod/mixin/compatibility/gl/GL30M.java"));

        assertTrue(source.contains("GlEmulationLog.warnContractGap(\"framebuffer_readback\", \"glBlitFramebuffer\""));
    }
}
