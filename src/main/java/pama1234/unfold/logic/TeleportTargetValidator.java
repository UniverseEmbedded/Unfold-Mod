package pama1234.unfold.logic;

import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

import java.util.Optional;

/**
 * 切层终点的唯一校验实现（common 侧）。
 *
 * <p>客户端预测与服务端授权都必须调用本类，避免「客户端觉得行、服务端觉得不行」的分叉。
 * 本类只依赖 Minecraft 的公共类型（{@link World}、{@link Entity}），因此可以放在 {@code src/main}
 * 而不是 {@code src/client}：A5 判定里用到的
 * {@code getBottomY / getTopYInclusive}（HeightLimitView）、
 * {@code isSpaceEmpty / getBlockCollisions / getEntityCollisions}（CollisionView）、
 * {@code getWorldBorder / findSupportingBlockPos} 都挂在 {@link World} 上，
 * {@code BlockState#getCollisionShape} 只需 {@link ShapeContext#of(Entity)}，与客户端类型无关。
 *
 * <p>A5 的第 8 步（投影不变量 Π 复核）依赖客户端的 {@code ProjectionBasis} 与当前展开方向，
 * 不能放进 common；它保留在
 * {@code pama1234.unfold.logic.UnfoldTeleportLogic#isCandidateClear} 里，
 * 调用本类完成第 1–7 步后再做第 8 步。服务端不做第 8 步：服务端没有展开方向，
 * 也不应该用客户端的投影参数去否决一次位置本身合法的落点。
 *
 * <p>检查顺序与客户端 A5 逐条对应（1 世界高度范围、2 世界边界、3 isSpaceEmpty、
 * 4 方块碰撞、5 实体碰撞、6 支撑存在、7 支撑顶面 == 脚底 Y）。
 * 改动本类的顺序或常量时，必须同步检查 {@code UnfoldTeleportLogic#isCandidateClear}
 * 与 {@code UnfoldMod} 的接收器；同理，改那边时也必须回到本类。
 */
public final class TeleportTargetValidator {

    private TeleportTargetValidator() {}

    /**
     * 脚底与支撑面高度的数值比较容差，与客户端 A5 完全一致
     * （{@code UnfoldTeleportLogic.SURFACE_TOLERANCE} 已改为引用本常量）。
     */
    public static final double SURFACE_TOLERANCE = 1.0e-4;

    /**
     * 服务端接受切层请求的距离上限（格）。
     *
     * <p>复核结论：合法切层的实际位移远小于此值，256 对合法请求形同虚设，只拦伪造包。
     * 依据是本仓库生成候选的几何上界：
     * <ul>
     *   <li>候选恒为 {@code p_desired + λd}，且 λ 由层证据给出。</li>
     *   <li>水平模式：{@code d ⊥ Y}，λ 直接等于沿 d 的越层距离，
     *       由 {@code MAX_DISTANCE = 64} 的射线长度加 clearance 约束为约 {@code 64.31}。</li>
     *   <li>垂直模式：{@code d ∥ Y}，{@code λ = Δy / |d.y| ≤ 64}。</li>
     *   <li>斜向模式：主路径沿 ±d 取证据，探测起点距玩家不超过约 0.31 格，
     *       {@code λ ≤ 64 + 0.31·cos(pitch) ≈ 64.26}。
     *       水平模式里的潜行下落/空中跨越虽然用竖直射线，但其调用点的基为 {@code d⊥Y}，
     *       {@code candidateOnLayer} 会因 {@code canChangeWorldY() == false} 返回 null，
     *       不产生候选，因此不参与上界。</li>
     * </ul>
     * 因此全部合法候选的位移不超过约 65 格（65 < 256），256 不构成玩法约束，
     * 只作为「明显不是在切层」的粗界，防止客户端发来任意坐标。
     * 详细推导见 {@code docs/SPEC-UNFOLD-ALGORITHM.md} 第 5、5.1 节。
     */
    public static final double MAX_REQUEST_DISTANCE = 256.0;

    /**
     * 拒绝原因枚举：客户端据此决定回退到 origin 还是保持预测位置。
     * 服务端只回这三个失败原因之一，或 {@link #NONE} 表示接受。
     */
    public enum RejectionReason {
        /** 校验通过，服务端已执行权威落位。 */
        NONE,
        /** 距离超限：请求点与玩家当前位置距离超过 {@link #MAX_REQUEST_DISTANCE}。 */
        DISTANCE_EXCEEDED,
        /** 无支撑：候选碰撞箱下方找不到支撑方块，或支撑面顶面与脚底 Y 不一致。 */
        NO_SUPPORT,
        /** 有碰撞：候选碰撞箱越出世界高度范围/世界边界，或与方块、实体碰撞箱相交。 */
        COLLISION
    }

    /**
     * 规格 A5 的第 1–7 步：校验终点位置本身是否合法（不涉及投影不变量）。
     *
     * <p>检查顺序与常量必须与客户端 {@code UnfoldTeleportLogic#isCandidateClear} 逐条一致，
     * 后者在本方法之后追加第 8 步 Π 复核。
     *
     * @param world   判定所在世界（客户端为 ClientWorld，服务端为 ServerWorld）。
     * @param entity  使用哪个实体的当前 pose 碰撞箱与 ShapeContext；两端都是发起切层的玩家。
     * @param feetPos 候选位置（玩家脚底锚点）。
     * @param feetY   层证据给出的支撑面高度。
     * @return {@link RejectionReason#NONE} 表示通过；否则为具体失败原因。
     */
    public static RejectionReason validateEndpoint(World world, Entity entity, Vec3d feetPos, double feetY) {
        // 当前 pose 的完整碰撞箱，平移到候选位置。
        Box box = entity.getBoundingBox().offset(feetPos.subtract(entity.getPos()));

        // 1. 世界合法高度范围。
        if (box.minY < world.getBottomY() || box.maxY > world.getTopYInclusive() + 1.0) {
            return RejectionReason.COLLISION;
        }
        // 2. 世界边界。
        if (!world.getWorldBorder().contains(box)) {
            return RejectionReason.COLLISION;
        }
        // 3. 主判据：完整空间占用（方块碰撞、实体碰撞与边界的语义由实体参数给出）。
        if (!world.isSpaceEmpty(entity, box)) {
            return RejectionReason.COLLISION;
        }
        // 4. 实际方块碰撞形状。
        for (VoxelShape shape : world.getBlockCollisions(entity, box)) {
            if (!shape.isEmpty()) {
                return RejectionReason.COLLISION;
            }
        }
        // 5. 实体重叠。
        for (VoxelShape shape : world.getEntityCollisions(entity, box)) {
            if (!shape.isEmpty()) {
                return RejectionReason.COLLISION;
            }
        }
        // 6. 支撑只作辅助：不能单独证明安全，但找不到支撑同样不能放行。
        Optional<BlockPos> supporting = world.findSupportingBlockPos(entity, box);
        if (supporting.isEmpty()) {
            return RejectionReason.NO_SUPPORT;
        }
        if (Math.abs(supportingTopY(world, entity, supporting.get()) - feetY) > SURFACE_TOLERANCE) {
            return RejectionReason.NO_SUPPORT;
        }
        // 7. 当前 pose 底部必须落在层证据给出的支撑面上。
        if (Math.abs(box.minY - feetY) > SURFACE_TOLERANCE) {
            return RejectionReason.NO_SUPPORT;
        }
        return RejectionReason.NONE;
    }

    /** 支撑方块顶面的世界高度；形状通过实体对应的 ShapeContext 取得。 */
    public static double supportingTopY(World world, Entity entity, BlockPos pos) {
        VoxelShape shape = world.getBlockState(pos).getCollisionShape(world, pos, ShapeContext.of(entity));
        double top = pos.getY();
        for (Box box : shape.getBoundingBoxes()) {
            top = Math.max(top, pos.getY() + box.maxY);
        }
        return top;
    }
}
