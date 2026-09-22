package net.vulkanmod.compat;

import com.sun.jna.Function;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import net.vulkanmod.Initializer;
import org.lwjgl.glfw.GLFW;

/**
 * Releases FCL/PojavLauncher's shared {@code ANativeWindow} from its GL stack so Vulkan can take it
 * over.
 *
 * <p>With the {@code opengles3_desktopgl_zink_kopper} renderer, FCL's GL bridge (gl_bridge.c) creates
 * an EGL window surface on the single {@code pojav_environ->pojavWindow}, and Mesa's zink driver
 * presents through <em>kopper</em> — its own Vulkan swapchain ({@code VkSurfaceKHR}) on the very same
 * ANativeWindow. Neither FCL's {@code glfwDestroyWindow} (a no-op on the shared window) nor
 * {@code glfwMakeContextCurrent(0)} tears any of that down, so {@code vkCreateAndroidSurfaceKHR}
 * fails with {@code VK_ERROR_NATIVE_WINDOW_IN_USE_KHR} (-1000000001). The GL4ES bridge has no
 * shutdown path at all ({@code pojavTerminate} only handles virgl), so the only way to release the
 * window is to fully tear the GL display down ourselves: destroy the EGL surface, destroy the EGL
 * context, then {@code eglTerminate} the display — the last of which destroys zink's Vulkan
 * device/instance and its kopper surface.
 *
 * <p>The catch is that JNA cannot {@code dlopen} FCL's {@code libEGL_mesa.so} directly: that library
 * depends on {@code libcutils.so}, which is not resolvable from the classloader linker namespace
 * ({@code clns-9}); FCL loads it in a private "escape" namespace instead. So a direct EGL bind
 * resolves to a <em>different</em> EGL instance ({@code libEGL_angle.so}) with no current surface.
 * The fix is to go through FCL itself: {@code libpojavexec.so} (loaded by
 * {@code System.loadLibrary("pojavexec")}) exports {@code getProcAddress(const char*)}, which
 * forwards to the EGL instance FCL actually loaded. We resolve the EGL functions through it and call
 * those pointers directly.
 */
public final class EglSurfaceReleaser {
    private static final int EGL_DRAW = 0x3059;

    private static EglLibrary egl;
    private static boolean resolvedViaPojavexec;
    private static Pointer fnGetCurrentDisplay;
    private static Pointer fnGetCurrentSurface;
    private static Pointer fnGetCurrentContext;
    private static Pointer fnGetError;
    private static Pointer fnMakeCurrent;
    private static Pointer fnDestroySurface;
    private static Pointer fnDestroyContext;
    private static Pointer fnTerminate;

    private EglSurfaceReleaser() {
    }

    /** {@code void *getProcAddress(const char *procname)} in {@code libpojavexec.so}. */
    private interface PojavexecLib extends Library {
        Pointer getProcAddress(String procname);
    }

    /**
     * @param windowHandle the GLFW "window" handle of the early OpenGL window. Under FCL's GL4ES
     *                     bridge this is in fact the {@code gl_render_window_t} bundle pointer, so
     *                     {@code glfwMakeContextCurrent} re-attaches its EGL surface/context to the
     *                     calling thread, after which the display/surface/context can be queried and
     *                     torn down.
     */
    public static void releaseWindowFromEgl(long windowHandle) {
        try {
            // Re-attach the early window's EGL context and surface to this thread. The early window
            // was created on a different thread, so without this eglGetCurrentSurface() would return
            // EGL_NO_SURFACE here and we would have no handle to destroy.
            GLFW.glfwMakeContextCurrent(windowHandle);

            long glfwCtx = GLFW.glfwGetCurrentContext();
            Initializer.LOGGER.info("VulkanMod: EGL handoff glfwCurrentContext=0x{}",
                    Long.toHexString(glfwCtx));

            resolveViaPojavexec();

            long display;
            long surface;
            long ctx;
            int err;

            if (fnGetCurrentDisplay != null) {
                display = invokeLong(fnGetCurrentDisplay);
                surface = fnGetCurrentSurface != null ? invokeLong(fnGetCurrentSurface, EGL_DRAW) : 0L;
                ctx = fnGetCurrentContext != null ? invokeLong(fnGetCurrentContext) : 0L;
                err = fnGetError != null ? invokeInt(fnGetError) : 0;
            } else {
                // Fallback: bind EGL directly. Only works when the EGL library is reachable from the
                // classloader namespace; on FCL this path resolves to a different EGL instance with
                // no current surface, hence the getProcAddress path above.
                if (egl == null) {
                    egl = loadEgl();
                }
                display = egl.eglGetCurrentDisplay();
                surface = egl.eglGetCurrentSurface(EGL_DRAW);
                ctx = egl.eglGetCurrentContext();
                err = egl.eglGetError();
            }

            Initializer.LOGGER.info("VulkanMod: EGL handoff display=0x{} surface=0x{} context=0x{} error=0x{}",
                    Long.toHexString(display), Long.toHexString(surface), Long.toHexString(ctx),
                    Integer.toHexString(err));

            if (display != 0L) {
                tearDown(display, surface, ctx);
            } else {
                Initializer.LOGGER.warn("VulkanMod: No current EGL display to tear down (surface=0x{}, context=0x{})",
                        Long.toHexString(surface), Long.toHexString(ctx));
            }
        } catch (Throwable t) {
            Initializer.LOGGER.error("VulkanMod: Failed to release the EGL surface from the ANativeWindow", t);
        }
    }

    /** Detach, then destroy surface + context and terminate the display to release zink's kopper surface. */
    private static void tearDown(long display, long surface, long ctx) {
        boolean viaPojavexec = fnMakeCurrent != null && fnTerminate != null;

        // Detach first (a surface must not be current while it is destroyed).
        if (viaPojavexec) {
            invokeInt(fnMakeCurrent, display, 0L, 0L, 0L);
        } else {
            egl.eglMakeCurrent(display, 0L, 0L, 0L);
        }

        if (surface != 0L) {
            if (viaPojavexec) {
                int destroyed = invokeInt(fnDestroySurface, display, surface);
                Initializer.LOGGER.info("VulkanMod: Destroyed EGL surface 0x{} (eglDestroySurface=0x{})",
                        Long.toHexString(surface), Integer.toHexString(destroyed));
            } else {
                egl.eglDestroySurface(display, surface);
                Initializer.LOGGER.info("VulkanMod: Destroyed EGL surface 0x{}", Long.toHexString(surface));
            }
        }

        if (ctx != 0L) {
            if (viaPojavexec) {
                int destroyed = invokeInt(fnDestroyContext, display, ctx);
                Initializer.LOGGER.info("VulkanMod: Destroyed EGL context 0x{} (eglDestroyContext=0x{})",
                        Long.toHexString(ctx), Integer.toHexString(destroyed));
            } else {
                egl.eglDestroyContext(display, ctx);
                Initializer.LOGGER.info("VulkanMod: Destroyed EGL context 0x{}", Long.toHexString(ctx));
            }
        }

        // eglTerminate destroys the entire display including zink's Vulkan device/instance, which is
        // what actually releases the kopper VkSurfaceKHR and disconnects the ANativeWindow.
        if (viaPojavexec) {
            int terminated = invokeInt(fnTerminate, display);
            Initializer.LOGGER.info("VulkanMod: Terminated EGL display 0x{} (eglTerminate=0x{})",
                    Long.toHexString(display), Integer.toHexString(terminated));
        } else {
            egl.eglTerminate(display);
            Initializer.LOGGER.info("VulkanMod: Terminated EGL display 0x{}", Long.toHexString(display));
        }
    }

    /** Resolve FCL's EGL functions through {@code libpojavexec.so}'s exported {@code getProcAddress}. */
    private static void resolveViaPojavexec() {
        if (resolvedViaPojavexec) {
            return;
        }
        resolvedViaPojavexec = true;
        try {
            PojavexecLib pojavexec = Native.load("pojavexec", PojavexecLib.class);
            fnGetCurrentDisplay = pojavexec.getProcAddress("eglGetCurrentDisplay");
            fnGetCurrentSurface = pojavexec.getProcAddress("eglGetCurrentSurface");
            fnGetCurrentContext = pojavexec.getProcAddress("eglGetCurrentContext");
            fnGetError = pojavexec.getProcAddress("eglGetError");
            fnMakeCurrent = pojavexec.getProcAddress("eglMakeCurrent");
            fnDestroySurface = pojavexec.getProcAddress("eglDestroySurface");
            fnDestroyContext = pojavexec.getProcAddress("eglDestroyContext");
            fnTerminate = pojavexec.getProcAddress("eglTerminate");
            Initializer.LOGGER.info("VulkanMod: Resolved EGL via libpojavexec getProcAddress " +
                            "(getCurrentDisplay={}, getCurrentSurface={}, destroySurface={}, destroyContext={}, terminate={})",
                    fnGetCurrentDisplay, fnGetCurrentSurface, fnDestroySurface, fnDestroyContext, fnTerminate);
        } catch (Throwable t) {
            Initializer.LOGGER.error("VulkanMod: libpojavexec getProcAddress resolution failed; falling back to direct EGL bind", t);
        }
    }

    private static long invokeLong(Pointer fn, Object... args) {
        return ((Long) Function.getFunction(fn).invoke(Long.class, args)).longValue();
    }

    private static int invokeInt(Pointer fn, Object... args) {
        return ((Integer) Function.getFunction(fn).invoke(Integer.class, args)).intValue();
    }

    private static EglLibrary loadEgl() {
        String nativeDir = System.getenv("POJAV_NATIVEDIR");
        String fullPath = System.getenv("SDL_EGL_LIBRARY");
        String[] candidates = {
                fullPath,
                nativeDir != null ? nativeDir + "/libEGL_mesa.so" : null,
                System.getenv("POJAVEXEC_EGL"),
                "libEGL_mesa.so",
                "libEGL_angle.so",
                "libEGL.so",
                "EGL",
        };
        for (String name : candidates) {
            if (name == null || name.isEmpty()) {
                continue;
            }
            try {
                EglLibrary lib = Native.load(name, EglLibrary.class);
                Initializer.LOGGER.info("VulkanMod: Loaded EGL library '{}' for the ANativeWindow handoff", name);
                return lib;
            } catch (Throwable t) {
                Initializer.LOGGER.info("VulkanMod: EGL library '{}' not loadable: {}", name, t.toString());
            }
        }
        throw new IllegalStateException("No EGL library could be loaded for the ANativeWindow handoff");
    }
}
