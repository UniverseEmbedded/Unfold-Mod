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
}