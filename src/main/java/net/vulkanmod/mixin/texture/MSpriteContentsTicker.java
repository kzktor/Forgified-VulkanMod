package net.vulkanmod.mixin.texture;

import net.vulkanmod.render.texture.SpriteUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.List;

@Mixin(targets = "net.minecraft.client.renderer.texture.SpriteContents$Ticker")
public class MSpriteContentsTicker {
    @Unique private static final String TICKER_CLASS =
            "net.minecraft.client.renderer.texture.SpriteContents$Ticker";
    @Unique private static final Field VULKANMOD_SUB_FRAME = findField(
            TICKER_CLASS, "subFrame", "f_244511_");
    @Unique private static final Field VULKANMOD_FRAME = findField(
            TICKER_CLASS, "frame", "f_244631_");
    @Unique private static final Field VULKANMOD_ANIMATION_INFO = findField(
            TICKER_CLASS, "animationInfo", "f_243921_");
    @Unique private static final Field VULKANMOD_FRAMES = findField(
            "net.minecraft.client.renderer.texture.SpriteContents$AnimatedTexture",
            "frames", "f_243714_");
    @Unique private static final Field VULKANMOD_FRAME_TIME = findField(
            "net.minecraft.client.renderer.texture.SpriteContents$FrameInfo",
            "time", "f_244553_");

    @Inject(method = "tickAndUpload", at = @At("HEAD"), cancellable = true)
    private void vulkanmod$advanceWithoutUpload(int x, int y, CallbackInfo ci) {
        if (SpriteUtil.shouldUpload()) {
            return;
        }

        int subFrame = getInt(VULKANMOD_SUB_FRAME, this) + 1;
        setInt(VULKANMOD_SUB_FRAME, this, subFrame);
        if (subFrame >= getFrameTime(this)) {
            List<?> frames = getFrames(this);
            int frame = (getInt(VULKANMOD_FRAME, this) + 1) % frames.size();
            setInt(VULKANMOD_FRAME, this, frame);
            setInt(VULKANMOD_SUB_FRAME, this, 0);
        }
        ci.cancel();
    }

    @Unique
    private static List<?> getFrames(Object ticker) {
        try {
            return (List<?>) VULKANMOD_FRAMES.get(VULKANMOD_ANIMATION_INFO.get(ticker));
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to access sprite animation frame data", e);
        }
    }

    @Unique
    private static int getFrameTime(Object ticker) {
        try {
            Object frameInfo = getFrames(ticker).get(getInt(VULKANMOD_FRAME, ticker));
            return VULKANMOD_FRAME_TIME.getInt(frameInfo);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to read sprite animation frame time", e);
        }
    }

    @Unique
    private static int getInt(Field field, Object target) {
        try {
            return field.getInt(target);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to read sprite ticker state", e);
        }
    }

    @Unique
    private static void setInt(Field field, Object target, int value) {
        try {
            field.setInt(target, value);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to write sprite ticker state", e);
        }
    }

    @Unique
    private static Field findField(String ownerName, String... alternatives) {
        try {
            Class<?> owner = Class.forName(ownerName);
            for (String fieldName : alternatives) {
                try {
                    Field field = owner.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    return field;
                } catch (NoSuchFieldException ignored) {
                }
            }
            throw new RuntimeException("Failed to resolve any of " + String.join(", ", alternatives)
                    + " on " + ownerName);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Failed to resolve class " + ownerName, e);
        }
    }
}
