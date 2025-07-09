package pama1234.unfold.util;

import net.minecraft.entity.player.PlayerEntity;

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
        return getClosest(player.getYaw(), player.getPitch());
    }

    public static ViewDirection getClosest(float yaw, float pitch) {
        // 【关键新增】健壮性检查，防止输入无效值
        if (Float.isNaN(yaw) || Float.isNaN(pitch) || Float.isInfinite(yaw) || Float.isInfinite(pitch)) {
            System.err.println("!!! WARNING: Invalid yaw/pitch provided to getClosest. Falling back to default.");
            return values()[0]; // 如果输入无效，直接返回一个默认值，避免后续计算崩溃
        }

        ViewDirection closest = values()[0];
        float minDistance = Float.MAX_VALUE;

        for (ViewDirection direction : values()) {
            float yawDiff = Math.abs(normalizeAngle(yaw - direction.yaw));
            float pitchDiff = Math.abs(pitch - direction.pitch);

            // 【关键新增】再次检查计算结果
            if (Float.isNaN(yawDiff) || Float.isNaN(pitchDiff)) {
                continue; // 如果计算出错，跳过这个方向
            }

            float distance = yawDiff + pitchDiff;

            if (distance < minDistance) {
                minDistance = distance;
                closest = direction;
            }
        }
        return closest;
    }

    // 平滑过渡函数
    public static float lerpAngle(float start, float end, float delta) {
        float diff = normalizeAngle(end - start);
        return normalizeAngle(start + diff * delta);
    }
}