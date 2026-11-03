package pama1234.unfold.logic;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import pama1234.unfold.util.ViewDirection;

public class UnfoldManager {
    public static boolean unfolding = false;
    public static ViewDirection currentDirection = ViewDirection.ISO_POS_X_POS_Y;
    /**
     * 【新增】用于存储当前所处的展开模式
     */
    public static UnfoldMode currentMode = UnfoldMode.ISOMETRIC;
    /**
     * 规格 A4：上一 tick 结束时的玩家位置（含瞬移结果），用于比较原版移动的实际投影位移。
     */
    public static Vec3d lastTickPosition = null;
    /**
     * 卡点 4：上一次水平切层所用的层方块与方向符号（+1 沿 +d，-1 沿 -d）。
     * 只用于抑制同一层在相邻两 tick 内的反向重穿，不改变 UnfoldModClient 的既有冷却语义。
     */
    public static BlockPos lastHorizontalCutBlock = null;
    public static double lastHorizontalCutSign = 0.0;
    /**
     * 层滞回（层序号单调性）：本次跳跃中已确定的换层方向，+1 表示向上（世界 Y 增大），
     * -1 表示向下，0 表示本次跳跃尚未换层。玩家落地即清零，因此约束只覆盖一次跳跃。
     * 选单调性而不选退避时间：不需要猜 tick 数，也不受帧率/延迟影响；代价是同一次跳跃内
     * 不能先向上再向下（反向换层会被拒绝），这是该选择已知的取舍。
     */
    public static int jumpLayerDirection = 0;
}