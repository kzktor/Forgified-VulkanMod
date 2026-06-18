package net.vulkanmod.gl;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GlTextureDimensionalContractTest {
    @Test
    void texImage3DRecordsDepthAndTargetAsDimensionalMetadata() throws Exception {
        String source = Files.readString(Path.of("src/main/java/net/vulkanmod/gl/GlTexture.java"));

        assertTrue(source.contains("recordDimensionalMetadata(target, level, internalFormat, width, height, depth, border, format, type)"));
        assertTrue(source.contains("depth"));
        assertTrue(source.contains("GL_TEXTURE_2D_ARRAY"));
        assertTrue(source.contains("GL_TEXTURE_3D"));
    }

    @Test
    void gl13DataOverloadsForwardByteBufferDataToDimensionalTextureLayer() throws Exception {
        String source = Files.readString(Path.of("src/main/java/net/vulkanmod/mixin/compatibility/gl/GL13M.java"));

        assertTrue(source.contains("GlTexture.texImage3D(target, level, internalformat, width, height, depth, border, format, type, data)"));
    }
}
