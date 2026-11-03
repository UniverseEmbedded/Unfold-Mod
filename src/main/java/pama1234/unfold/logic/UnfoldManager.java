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

    /**
     * 服务端权威切层：请求包发送后的最小间隔（客户端 tick）。
     * 依据：切层发生在 END_CLIENT_TICK，请求最早要到服务端下一 tick 才被处理，
     * 间隔 2 tick 保证两次请求之间至少隔一个服务端 tick，不会在同一 tick 叠发。
     */
    public static final int TELEPORT_REQUEST_INTERVAL_TICKS = 2;
    /**
     * 待确认预测的超时（客户端 tick）。依据：与服务端 handlePendingTeleport 的
     * 20 tick 重发窗口一致，超过这个窗口仍未回执就认定丢失。
     */
    public static final int PENDING_TELEPORT_TIMEOUT_TICKS = 20;

    /**
     * 客户端预测：待服务端确认的切层目标。非 null 表示有一个未确认的请求在途；
     * 本地位置只是预测，不是权威结果。
     */
    public static Vec3d pendingTeleportTarget = null;
    /** 预测前的位置，供服务端拒绝（距离超限 / 有碰撞）时回退；无支撑拒绝时保持预测位置。 */
    public static Vec3d pendingTeleportOrigin = null;
    /** 待确认预测已持续的客户端 tick 数，用于超时丢弃。 */
    public static int pendingTeleportTicks = 0;
    /** 发送请求后的剩余禁止发送 tick 数，与既有 teleportCooldown 独立叠加。 */
    public static int teleportRequestCooldownTicks = 0;

    /**
     * 丢弃待确认预测：不回写目标位置（拉回后服务端位置已是权威值），
     * 并重置请求间隔冷却，避免下一 tick 立刻重发。既有 teleportCooldown 的
     * 「落地即解除」语义不变。
     */
    public static void discardPendingTeleport() {
        pendingTeleportTarget = null;
        pendingTeleportOrigin = null;
        pendingTeleportTicks = 0;
        teleportRequestCooldownTicks = TELEPORT_REQUEST_INTERVAL_TICKS;
    }
}