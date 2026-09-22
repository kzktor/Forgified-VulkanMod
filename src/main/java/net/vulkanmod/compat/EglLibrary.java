package net.vulkanmod.compat;

import com.sun.jna.Library;

/**
 * Minimal JNA binding for the subset of EGL needed to release FCL/PojavLauncher's shared
 * {@code ANativeWindow} before it is handed off to Vulkan.
 *
 * <p>On FCL the launcher's GL4ES bridge (gl_bridge.c) creates an EGL window surface on the single
 * {@code pojav_environ->pojavWindow} during Forge's early-display window. Neither
 * {@code glfwDestroyWindow} (a Java-side no-op) nor {@code glfwMakeContextCurrent(0)} (which only
 * detaches via {@code eglMakeCurrent}) destroys that surface, so {@code vkCreateAndroidSurfaceKHR}
 * fails with {@code VK_ERROR_NATIVE_WINDOW_IN_USE_KHR} (-1000000001). Destroying the surface via
 * {@code eglDestroySurface} calls {@code native_window_api_disconnect(NATIVE_WINDOW_API_EGL)} and
 * releases the window for Vulkan.
 */
public interface EglLibrary extends Library {
    long eglGetCurrentDisplay();

    long eglGetCurrentSurface(int readdraw);

    long eglGetCurrentContext();

    int eglMakeCurrent(long dpy, long draw, long read, long ctx);

    int eglDestroySurface(long dpy, long surface);

    int eglDestroyContext(long dpy, long ctx);

    int eglTerminate(long dpy);

    int eglGetError();
}
