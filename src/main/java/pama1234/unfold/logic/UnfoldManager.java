package pama1234.unfold.logic;

import pama1234.unfold.util.ViewDirection;

public class UnfoldManager {
    public static boolean unfolding = false;
    public static ViewDirection currentDirection = ViewDirection.ISO_POS_X_POS_Y;
    public static long transitionStartTick = 0;
    public static final long TRANSITION_TICKS = 10; // 0.5秒平滑过渡

    public static float getTransitionProgress(float tickDelta) {
        long elapsed = System.currentTimeMillis() / 50 - transitionStartTick;
        if (elapsed >= TRANSITION_TICKS) {
            return 1.0f;
        }
        return (elapsed + tickDelta) / (float) TRANSITION_TICKS;
    }
}