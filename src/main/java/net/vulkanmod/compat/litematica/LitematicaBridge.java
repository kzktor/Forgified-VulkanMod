package net.vulkanmod.compat.litematica;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.vulkanmod.Initializer;
import net.vulkanmod.compat.CompatDetector;
import org.joml.Matrix4f;

import java.lang.reflect.Method;

/**
 * Drives Litematica's schematic world renderer.
 * <p>
 * Litematica renders its schematic piecewise from {@code @Inject}s at the TAIL of
 * {@code LevelRenderer#setupRender}, {@code LevelRenderer#renderChunkLayer} and
 * {@code LevelRenderer#allChanged}. VulkanMod replaces all three with a HEAD inject that cancels,
 * so the method returns before ever reaching those TAIL callbacks: the schematic is prepared,
 * updated and drawn zero times, which is why nothing shows up at all.
 * <p>
 * The mixin cannot hand control back to a cancelled method body, so VulkanMod calls Litematica's
 * entry points itself, at the same points in the frame vanilla would have reached them. Everything
 * is reflective because Litematica is a Fabric mod pulled in through Sinytra Connector and is not
 * on the compile classpath.
 *
 * @see net.vulkanmod.render.VBO#drawChunkLayer() the other half of the fix - Litematica's per-chunk
 * {@code VertexBuffer#draw()} calls need the vanilla shader's Vulkan pipeline bound for them
 */
public final class LitematicaBridge {
    private static final String MOD_ID = "litematica";
    private static final String RENDERER_CLASS = "fi.dy.masa.litematica.render.LitematicaRenderer";

    private static boolean initialized;
    private static volatile boolean available;

    private static Object renderer;
    private static Method loadRenderers;
    private static Method piecewisePrepareAndUpdate;
    private static Method piecewiseRenderSolid;
    private static Method piecewiseRenderCutoutMipped;
    private static Method piecewiseRenderCutout;
    private static Method piecewiseRenderTranslucent;
    private static Method piecewiseRenderOverlay;

    private LitematicaBridge() {
    }

    private static synchronized void init() {
        if (initialized)
            return;
        initialized = true;

        if (!CompatDetector.isModLoaded(MOD_ID))
            return;

        try {
            Class<?> rendererClass = Class.forName(RENDERER_CLASS);
            renderer = rendererClass.getMethod("getInstance").invoke(null);
            if (renderer == null)
                return;

            loadRenderers = method(rendererClass, "loadRenderers", 0);
            piecewisePrepareAndUpdate = method(rendererClass, "piecewisePrepareAndUpdate", 1);
            piecewiseRenderSolid = method(rendererClass, "piecewiseRenderSolid", 2);
            piecewiseRenderCutoutMipped = method(rendererClass, "piecewiseRenderCutoutMipped", 2);
            piecewiseRenderCutout = method(rendererClass, "piecewiseRenderCutout", 2);
            piecewiseRenderTranslucent = method(rendererClass, "piecewiseRenderTranslucent", 2);
            piecewiseRenderOverlay = method(rendererClass, "piecewiseRenderOverlay", 2);

            available = true;
            Initializer.LOGGER.info("Litematica schematic render bridge enabled for {} {}",
                    MOD_ID, CompatDetector.getModVersion(MOD_ID));
        } catch (Throwable t) {
            Initializer.LOGGER.warn("Could not hook Litematica's schematic renderer: {}", t.toString());
        }
    }

    /**
     * Resolves a Litematica method by name and arity. Its parameter types are remapped Minecraft
     * classes, so matching on them would tie this bridge to one mapping flavour.
     */
    private static Method method(Class<?> owner, String name, int parameterCount) throws NoSuchMethodException {
        for (Method candidate : owner.getMethods()) {
            if (candidate.getName().equals(name) && candidate.getParameterCount() == parameterCount)
                return candidate;
        }

        throw new NoSuchMethodException("%s.%s/%d".formatted(owner.getName(), name, parameterCount));
    }

    /**
     * Stands in for Litematica's {@code allChanged} (yarn {@code reload}) RETURN hook, reloading the
     * schematic chunk renderers alongside the vanilla ones. Mirrors its guard: only reload while the
     * renderer belongs to the level the client is actually in.
     */
    public static void afterAllChanged(ClientLevel level) {
        if (!initialized)
            init();
        if (!available)
            return;

        if (level == null || level != Minecraft.getInstance().level)
            return;

        invoke(loadRenderers);
    }

    /**
     * Stands in for Litematica's {@code setupRender} (yarn {@code setupTerrain}) TAIL hook: culls
     * schematic chunks against this frame's frustum and runs its chunk rebuild/upload queue. Without
     * it the schematic never gets any geometry built, so nothing can be drawn later in the frame.
     */
    public static void afterSetupRender(Frustum frustum) {
        if (!initialized)
            init();
        if (!available)
            return;

        invoke(piecewisePrepareAndUpdate, frustum);
    }

    /**
     * Stands in for Litematica's {@code renderChunkLayer} (yarn {@code renderLayer}) TAIL hook,
     * drawing the schematic layer that matches the terrain layer just rendered. The overlay (block
     * outlines and side quads) rides along with the translucent layer exactly as Litematica does it,
     * which also runs its per-frame cleanup.
     */
    public static void afterRenderChunkLayer(RenderType renderType, PoseStack poseStack, Matrix4f projectionMatrix) {
        if (!initialized)
            init();
        if (!available)
            return;

        if (renderType == RenderType.solid()) {
            invoke(piecewiseRenderSolid, poseStack, projectionMatrix);
        } else if (renderType == RenderType.cutoutMipped()) {
            invoke(piecewiseRenderCutoutMipped, poseStack, projectionMatrix);
        } else if (renderType == RenderType.cutout()) {
            invoke(piecewiseRenderCutout, poseStack, projectionMatrix);
        } else if (renderType == RenderType.translucent()) {
            invoke(piecewiseRenderTranslucent, poseStack, projectionMatrix);
            invoke(piecewiseRenderOverlay, poseStack, projectionMatrix);
        }
    }

    private static void invoke(Method method, Object... args) {
        try {
            method.invoke(renderer, args);
        } catch (Throwable t) {
            available = false;
            Initializer.LOGGER.error("Disabling Litematica schematic render bridge after failure in {}",
                    method.getName(), t);
        }
    }
}
