package net.vulkanmod.vulkan;

import net.vulkanmod.Initializer;
import org.lwjgl.system.Configuration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Extracts VulkanMod's platform-specific LWJGL libraries from the mod JAR. */
public final class NativeLibraryLoader {
    private static boolean loaded;

    private NativeLibraryLoader() {
    }

    public static synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }

        String os = detectOs();
        String architecture = detectArchitecture();
        String platformPath = os + "/" + architecture + "/" + os + "/" + architecture + "/org/lwjgl/";
        String[] libraries = switch (os) {
            case "windows" -> new String[]{"shaderc/shaderc.dll", "vma/lwjgl_vma.dll"};
            case "linux" -> new String[]{"shaderc/libshaderc.so", "vma/liblwjgl_vma.so"};
            case "macos" -> new String[]{"shaderc/libshaderc.dylib", "vma/liblwjgl_vma.dylib", "vulkan/libMoltenVK.dylib"};
            default -> throw new UnsupportedOperationException("Unsupported operating system: " + os);
        };

        try {
            Path directory = Files.createTempDirectory("vulkanmod-natives-");
            directory.toFile().deleteOnExit();

            for (String library : libraries) {
                String resource = "/assets/vulkanmod/natives/" + platformPath + library;
                String fileName = library.substring(library.lastIndexOf('/') + 1);
                Path extracted = directory.resolve(fileName);

                try (InputStream input = NativeLibraryLoader.class.getResourceAsStream(resource)) {
                    if (input == null) {
                        throw new IOException("Missing native resource " + resource);
                    }
                    Files.copy(input, extracted);
                    extracted.toFile().deleteOnExit();
                }
            }

            Configuration.LIBRARY_PATH.set(directory.toString());
            loaded = true;
            Initializer.LOGGER.info("VulkanMod: extracted LWJGL natives for {} {} to {}", os, architecture, directory);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Unable to extract VulkanMod native libraries", exception);
        }
    }

    private static String detectOs() {
        String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (name.contains("win")) {
            return "windows";
        }
        if (name.contains("mac") || name.contains("darwin")) {
            return "macos";
        }
        if (name.contains("linux")) {
            return "linux";
        }
        throw new UnsupportedOperationException("Unsupported operating system: " + name);
    }

    private static String detectArchitecture() {
        String architecture = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (architecture.equals("aarch64") || architecture.equals("arm64")) {
            return "arm64";
        }
        if (architecture.equals("amd64") || architecture.equals("x86_64") || architecture.equals("x86-64")) {
            return "x64";
        }
        throw new UnsupportedOperationException("Unsupported architecture: " + architecture);
    }
}
