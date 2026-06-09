package net.vulkanmod.compat.capabilities;

public final class ExternalRenderPathOptions {
    private static final String EXTERNAL_LOD = "vulkanmod.compat.externalLod";
    private static final String EXTERNAL_LOD_DRAW = "vulkanmod.compat.externalLod.draw";
    private static final String EXTERNAL_LOD_DEBUG_DRAW = "vulkanmod.compat.externalLod.debugDraw";

    private static final String LEGACY_EXTERNAL_LOD = "vulkanmod.compat.distanthorizons";
    private static final String LEGACY_EXTERNAL_LOD_DRAW = "vulkanmod.compat.distanthorizons.draw";
    private static final String LEGACY_EXTERNAL_LOD_DEBUG_DRAW = "vulkanmod.compat.distanthorizons.debugDraw";

    private ExternalRenderPathOptions() {
    }

    public static boolean externalLodEnabled() {
        return "on".equalsIgnoreCase(getProperty(EXTERNAL_LOD, LEGACY_EXTERNAL_LOD, "off"));
    }

    public static boolean externalLodDrawEnabled() {
        return Boolean.parseBoolean(getProperty(EXTERNAL_LOD_DRAW, LEGACY_EXTERNAL_LOD_DRAW, "true"));
    }

    public static boolean externalLodDebugDrawEnabled() {
        return Boolean.parseBoolean(getProperty(EXTERNAL_LOD_DEBUG_DRAW, LEGACY_EXTERNAL_LOD_DEBUG_DRAW, "false"));
    }

    private static String getProperty(String primary, String legacy, String defaultValue) {
        String value = System.getProperty(primary);
        if (value != null) {
            return value;
        }

        value = System.getProperty(legacy);
        return value != null ? value : defaultValue;
    }
}
