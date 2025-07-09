package pama1234.unfold.util;

import net.minecraft.entity.player.PlayerEntity;
import pama1234.unfold.UnfoldMod;

public enum ViewDirection {
    // 8个斜侧方视角 (纪念碑谷风格)
    ISO_POS_X_POS_Y(45.0f, -35.26f),
    ISO_POS_X_NEG_Y(135.0f, -35.26f),
    ISO_NEG_X_POS_Y(-45.0f, -35.26f),
    ISO_NEG_X_NEG_Y(-135.0f, -35.26f),
    ISO_POS_X_POS_Y_2(45.0f, 35.26f),
    ISO_POS_X_NEG_Y_2(135.0f, 35.26f),
    ISO_NEG_X_POS_Y_2(-45.0f, 35.26f),
    ISO_NEG_X_NEG_Y_2(-135.0f, 35.26f),

    // 6个水平/垂直视角 (FEZ风格)
    POS_X(90.0f, 0.0f),
    NEG_X(-90.0f, 0.0f),
    POS_Z(0.0f, 0.0f),
    NEG_Z(180.0f, 0.0f),
    POS_Y(0.0f, -90.0f),
    NEG_Y(0.0f, 90.0f);

    public final float yaw;
    public final float pitch;

    ViewDirection(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    private static float normalizeAngle(float angle) {
        float result = angle % 360.0f;
        if (result <= -180.0f) {
            result += 360.0f;
        }
        if (result > 180.0f) {
            result -= 360.0f;
        }
        return result;
    }

    public static ViewDirection getClosest(PlayerEntity player) {
        UnfoldMod.LOGGER.info("[GET-CLOSEST] Called with player entity.");
        return getClosest(player.getYaw(), player.getPitch());
    }

    public static ViewDirection getClosest(float yaw, float pitch) {
        UnfoldMod.LOGGER.info(String.format("[GET-CLOSEST] Starting calculation for Y/P: %.2f / %.2f", yaw, pitch));

        if (Float.isNaN(yaw) || Float.isNaN(pitch) || Float.isInfinite(yaw) || Float.isInfinite(pitch)) {
            UnfoldMod.LOGGER.severe("[GET-CLOSEST] !!! FATAL: Invalid yaw/pitch provided. Input was NaN or Infinite. Falling back to default.");
            return values()[0];
        }

        ViewDirection closest = values()[0];
        float minDistance = Float.MAX_VALUE;

        for (ViewDirection direction : values()) {
            UnfoldMod.LOGGER.info("[GET-CLOSEST-LOOP] --- Checking direction: " + direction.name());
            float yawDiff = Math.abs(normalizeAngle(yaw - direction.yaw));
            float pitchDiff = Math.abs(pitch - direction.pitch);

            UnfoldMod.LOGGER.info(String.format("[GET-CLOSEST-LOOP] YawDiff: %.2f, PitchDiff: %.2f", yawDiff, pitchDiff));

            if (Float.isNaN(yawDiff) || Float.isNaN(pitchDiff)) {
                UnfoldMod.LOGGER.warning("[GET-CLOSEST-LOOP] !!! Calculation resulted in NaN. Skipping direction: " + direction.name());
                continue;
            }

            float distance = yawDiff + pitchDiff;
            UnfoldMod.LOGGER.info(String.format("[GET-CLOSEST-LOOP] Total distance: %.2f. Current minDistance: %.2f", distance, minDistance));

            if (distance < minDistance) {
                minDistance = distance;
                closest = direction;
                UnfoldMod.LOGGER.info("[GET-CLOSEST-LOOP] >>> New closest direction found: " + closest.name());
            }
        }

        UnfoldMod.LOGGER.info("[GET-CLOSEST] Final closest direction: " + closest.name());
        return closest;
    }

    // 平滑过渡函数
    public static float lerpAngle(float start, float end, float delta) {
        float diff = normalizeAngle(end - start);
        return normalizeAngle(start + diff * delta);
    }
}