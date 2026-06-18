package net.vulkanmod.gl;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlEmulationLogContractTest {
    @Test
    void contractGapKeyIncludesFamilyAndOperation() throws Exception {
        Method method = GlEmulationLog.class.getDeclaredMethod("contractGapKey", String.class, String.class);
        method.setAccessible(true);

        assertEquals("texture_image.glTexImage3D", method.invoke(null, "texture_image", "glTexImage3D"));
        assertEquals("framebuffer_readback.glReadPixels", method.invoke(null, "framebuffer_readback", "glReadPixels"));
    }

    @Test
    void contractGapLoggingAcceptsStableFamilyAndOperationNames() {
        assertDoesNotThrow(() -> GlEmulationLog.warnContractGap(
                "shader_conversion",
                "externalShaderFallback",
                "Generic shader fallback used for unsupported GLSL contract"));
    }

    @Test
    void contractGapRejectsModNamedFamilies() throws Exception {
        Method method = GlEmulationLog.class.getDeclaredMethod("contractGapKey", String.class, String.class);
        method.setAccessible(true);

        ReflectiveOperationException exception = assertThrows(ReflectiveOperationException.class,
                () -> method.invoke(null, "flywheel", "backend"));
        Throwable cause = exception.getCause();
        assertTrue(cause instanceof IllegalArgumentException);
        assertTrue(cause.getMessage().contains("GL contract family"));
    }

    @Test
    void contractGapRejectsNullFamilyWithClearException() throws Exception {
        Method method = GlEmulationLog.class.getDeclaredMethod("contractGapKey", String.class, String.class);
        method.setAccessible(true);

        ReflectiveOperationException exception = assertThrows(ReflectiveOperationException.class,
                () -> method.invoke(null, null, "glReadPixels"));
        Throwable cause = exception.getCause();
        assertTrue(cause instanceof IllegalArgumentException);
        assertTrue(cause.getMessage().contains("GL contract family"));
    }
}
