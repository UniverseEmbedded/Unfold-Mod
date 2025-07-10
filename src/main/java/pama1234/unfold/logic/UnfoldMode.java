package pama1234.unfold.logic;

import pama1234.unfold.util.ViewDirection;

public enum UnfoldMode {
    VERTICAL,
    HORIZONTAL,
    ISOMETRIC;

    /**
     * 根据玩家的视角方向，判断应该进入哪种展开模式。
     * 修正：直接根据玩家的俯仰角来判断是否为垂直模式，而不是依赖 getClosest() 的结果。
     *
     * @param playerPitch 玩家的俯仰角 (Pitch)。
     * @param direction   通过 getClosest() 计算出的，用于水平和斜角模式的方向。
     * @return 最终确定的 UnfoldMode。
     */
    public static UnfoldMode fromPlayerPitch(float playerPitch, ViewDirection direction) {
        final float VERTICAL_THRESHOLD = 75.0f;

        // 优先根据玩家的实际俯仰角判断
        if (playerPitch < -VERTICAL_THRESHOLD || playerPitch > VERTICAL_THRESHOLD) {
            return VERTICAL;
        }

        // 如果不是垂直模式，再根据 ViewDirection 的 pitch 判断是水平还是斜角
        if (direction.pitch == 0.0f) {
            return HORIZONTAL;
        }

        return ISOMETRIC;
    }

}