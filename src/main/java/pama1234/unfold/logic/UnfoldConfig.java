package pama1234.unfold.logic;

import net.minecraft.util.math.MathHelper;

public class UnfoldConfig {
    // --- [修改] scale 是可调字段（由 changeScale 修改），不是编译期常量 ---
    public static float scale = 8.0f; // 默认缩放值

    // --- [新增] 从OrthoCam借鉴的缩放参数，用于控制缩放行为 ---
    /** 最小缩放值 (放大上限) */
    public static final float MIN_SCALE = 0.5f;
    /** 最大缩放值 (缩小上限) */
    public static final float MAX_SCALE = 128.0f;
    /** 每次缩放的乘数，提供更平滑的缩放体验 */
    public static final float SCALE_STEP_MULTIPLIER = 1.1f;

    // --- 核心参数，保持不变 ---
    public static final float MIN_DISTANCE = -1000.0f;
    public static final float MAX_DISTANCE = 1000.0f;
    public static final boolean AUTO_THIRD_PERSON = true;


    /**
     * [新增] 更改相机缩放值的方法
     * @param zoomOut 如果为 true，则缩小视图 (scale值变大)；如果为 false，则放大视图 (scale值变小)。
     */
    public static void changeScale(boolean zoomOut) {
        if (zoomOut) {
            scale *= SCALE_STEP_MULTIPLIER;
        } else {
            scale /= SCALE_STEP_MULTIPLIER;
        }
        // 使用MathHelper.clamp确保缩放值在预设的最小/最大范围内
        scale = MathHelper.clamp(scale, MIN_SCALE, MAX_SCALE);
    }
}