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
    POS_Y(0.0f, -90.0f), // 俯视
    NEG_Y(0.0f, 90.0f);  // 仰视

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

    /**
     * 根据玩家的视角（偏航角和俯仰角）找到最接近的预设视角方向。
     * 修正：优先判断垂直视角，如果玩家的俯仰角接近-90°或+90°，则直接返回对应的垂直方向，忽略偏航角。
     *
     * @param yaw   玩家的偏航角 (Yaw)。
     * @param pitch 玩家的俯仰角 (Pitch)。
     * @return 最接近的 ViewDirection。
     */
    public static ViewDirection getClosest(float yaw, float pitch) {
        if (Float.isNaN(yaw) || Float.isNaN(pitch) || Float.isInfinite(yaw) || Float.isInfinite(pitch)) {
            return values()[0];
        }

        // 【修改开始】
        // 设定一个阈值，比如俯仰角超过75度就认为是垂直视角
        final float VERTICAL_THRESHOLD = 75.0f;

        // 如果玩家是俯视
        if (pitch < -VERTICAL_THRESHOLD) {
            return ViewDirection.POS_Y;
        }
        // 如果玩家是仰视
        if (pitch > VERTICAL_THRESHOLD) {
            return ViewDirection.NEG_Y;
        }
        // 【修改结束】

        // 如果不是垂直视角，再执行原有的最近距离查找逻辑
        ViewDirection closest = values()[0];
        float minDistance = Float.MAX_VALUE;

        for (ViewDirection direction : values()) {
            // 跳过垂直方向，因为已经处理过了
            if (direction == POS_Y || direction == NEG_Y) {
                continue;
            }

            float yawDiff = Math.abs(normalizeAngle(yaw - direction.yaw));
            float pitchDiff = Math.abs(pitch - direction.pitch);
            // 保持原有的距离计算方式，但现在它只在非垂直视角中起作用
            float distance = yawDiff + pitchDiff;

            if (distance < minDistance) {
                minDistance = distance;
                closest = direction;
            }
        }
        return closest;
    }
}