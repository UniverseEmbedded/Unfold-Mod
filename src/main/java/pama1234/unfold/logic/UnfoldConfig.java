package pama1234.unfold.logic;

public class UnfoldConfig {
    // --- 核心参数，直接从 OrthoCamera 借鉴 ---
    public static final float SCALE = 8.0f; // 你的固定缩放值
    public static final float MIN_DISTANCE = -1000.0f; // 【关键】近裁剪面
    public static final float MAX_DISTANCE = 1000.0f; // 【关键】远裁剪面

    // 我们也把这个逻辑搬过来，尽管在unfold里总是启用的
    public static final boolean AUTO_THIRD_PERSON = true;
}