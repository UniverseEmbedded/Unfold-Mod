package pama1234.unfold.util;

import net.minecraft.util.math.Vec3d;

/**
 * 唯一的投影基（规格 A1）。
 *
 * 同时供相机角度、玩家输入映射、位置投影、LayerRaycaster、层滞回和目标位置重建使用。
 * 这里只保存几何定义，不做安全判定，也不读取世界。
 *
 * 依据：`docs/SPEC-UNFOLD-ALGORITHM.md` 第 1 节（A1）。
 * 除 {@link #ISO_PITCH_DEGREES} 的取值说明外，本类按规格的 [推断：几何定义] 实现，
 * 尚未在运行时验证其与相机实际成像完全一致。
 */
public final class ProjectionBasis {

    /**
     * 8 个斜角预设共用的俯仰角 = arctan(1/√2) 的标准等轴测角。
     *
     * 来源判定：初始提交 `41c4a54 init`（2025-07-09）直接手写 35.26；
     * 仓库与文档中没有“从游戏实测得到”的记录。Y端71-B L556 与 Y端70-B L9471
     * 都把该角度写作 arctan(1/√2) ≈ 35.26438968° 的近似值。
     * 因此按手写近似处理，改用精确值 35.26438968；与原值相差约 0.0044°。
     */
    public static final float ISO_PITCH_DEGREES = 35.26438968f;

    private static final double DEG_TO_RAD = Math.PI / 180.0;
    private static final double EPSILON = 1.0e-9;

    private final float yawDegrees;
    private final float pitchDegrees;
    /** 展开轴 d：投影后被压缩掉的深度方向，也是相机前向。 */
    private final Vec3d axis;
    /** 屏幕上方向 e_u。 */
    private final Vec3d screenUp;
    /** 屏幕右方向 e_r。 */
    private final Vec3d screenRight;

    private ProjectionBasis(float yawDegrees, float pitchDegrees) {
        this.yawDegrees = yawDegrees;
        this.pitchDegrees = pitchDegrees;

        double yawRad = yawDegrees * DEG_TO_RAD;
        double pitchRad = pitchDegrees * DEG_TO_RAD;

        // 与 UnfoldTeleportLogic.getUnfoldAxisDirection 一致的 Minecraft 相机前向公式。
        // 后者只转调 ProjectionBasis.of(...).axis()，本类才是该公式的唯一实现。
        this.axis = new Vec3d(
                -Math.sin(yawRad) * Math.cos(pitchRad),
                -Math.sin(pitchRad),
                Math.cos(yawRad) * Math.cos(pitchRad)
        ).normalize();

        // e_u = normalize(Y - (Y·d)d)，规格 A1.2。
        Vec3d worldUp = new Vec3d(0.0, 1.0, 0.0);
        Vec3d upCandidate = worldUp.subtract(this.axis.multiply(worldUp.dotProduct(this.axis)));
        if (upCandidate.lengthSquared() < EPSILON) {
            // 退化分支：d ∥ Y 时分母为零。
            // [推断] 规格要求“选定相机水平朝向对应的屏幕上方向”，这里取水平前向。
            Vec3d horizontal = new Vec3d(-Math.sin(yawRad), 0.0, Math.cos(yawRad));
            if (horizontal.lengthSquared() < EPSILON) {
                horizontal = new Vec3d(0.0, 0.0, 1.0);
            }
            this.screenUp = horizontal.normalize();
        } else {
            this.screenUp = upCandidate.normalize();
        }

        // e_r = normalize(d × e_u)，规格 A1.3。
        // 静态已证实：e_r 就是相机的屏幕右向。
        // 1.21.5 的 Camera.setRotation 为 rotationYXZ(π - yaw, -pitch, 0)（Y·X·Z 顺序），
        // GameRenderer 取其共轭构造视图矩阵（纯旋转，无镜像）。
        // 记 R = Ry(π - yaw)·Rx(-pitch)，则 R·(1,0,0) = (-cos yaw, 0, -sin yaw) = d × e_u，
        // 故相机屏幕右向与本类 screenRight 完全一致，无需反号。
        // 未覆盖项：玩家实体 yaw 未被锁定（Mouse 直接调 changeLookDirection），
        // 鼠标移动会让原版输入的世界方向偏离本基；这只影响输入映射，不改变本基的符号。
        this.screenRight = this.axis.crossProduct(this.screenUp).normalize();
    }

    public static ProjectionBasis of(ViewDirection direction) {
        return new ProjectionBasis(direction.yaw, direction.pitch);
    }

    public static ProjectionBasis fromAngles(float yawDegrees, float pitchDegrees) {
        return new ProjectionBasis(yawDegrees, pitchDegrees);
    }

    /** 展开轴 d。 */
    public Vec3d axis() {
        return axis;
    }

    /** 屏幕上方向 e_u。 */
    public Vec3d screenUp() {
        return screenUp;
    }

    /** 屏幕右方向 e_r。 */
    public Vec3d screenRight() {
        return screenRight;
    }

    public float yawDegrees() {
        return yawDegrees;
    }

    public float pitchDegrees() {
        return pitchDegrees;
    }

    /** Π(p) = [p·e_r, p·e_u]，规格 A1.4。 */
    public ScreenPos project(Vec3d point) {
        return new ScreenPos(point.dotProduct(screenRight), point.dotProduct(screenUp));
    }

    /**
     * 规格 A2：展开轴是否能通过 λ 改变世界 Y。
     *
     * 本基按 A1 构造，恒有 e_u·d = 0、e_r·d = 0，因此 Π(d) = (0, 0)；
     * 无论 d 是否平行于 Y，Π 的核都包含 span(d)。
     * 该判断只用于区分“层顶面 Y 反解 λ”是否可用，不改变投影秩。
     */
    public boolean canChangeWorldY() {
        return Math.abs(axis.y) >= EPSILON;
    }

    /**
     * 规格 A3：硬检查 Π(candidate) = Π(anchor)。
     *
     * tolerance 只用于浮点数值比较，不允许用来掩盖系统性坐标偏移。
     */
    public boolean preservesProjection(Vec3d anchor, Vec3d candidate, double tolerance) {
        ScreenPos from = project(anchor);
        ScreenPos to = project(candidate);
        double dr = to.right() - from.right();
        double du = to.up() - from.up();
        return dr * dr + du * du <= tolerance * tolerance;
    }

    /** 沿展开轴的自由量 λ。给定基准点与目标点，λ = (target - anchor)·d。 */
    public double lambdaFor(Vec3d anchor, Vec3d target) {
        return target.subtract(anchor).dotProduct(axis);
    }

    /** p(λ) = anchor + λd，规格 A1.5。 */
    public Vec3d reconstruct(Vec3d anchor, double lambda) {
        return anchor.add(axis.multiply(lambda));
    }

    /**
     * 把玩家位置沿展开轴移动到指定 Y 平面，X/Z 随之变化，屏幕投影不变。
     * λ = (targetY - anchor.y) / d.y。
     *
     * 当 d⊥Y 时，沿 d 无法改变世界 Y：返回 anchor（λ=0），不再退回“只改 Y”的旧退化行为，
     * 因为只改 Y 会违反 Π(p_c) = Π(p_anchor) 的硬约束。水平模式应改用 {@link #reconstruct}。
     */
    public Vec3d reconstructAtY(Vec3d anchor, double targetY) {
        if (!canChangeWorldY()) {
            return anchor;
        }
        double lambda = (targetY - anchor.y) / axis.y;
        return reconstruct(anchor, lambda);
    }

    /** 屏幕坐标（右、上）。 */
    public record ScreenPos(double right, double up) {
    }
}
