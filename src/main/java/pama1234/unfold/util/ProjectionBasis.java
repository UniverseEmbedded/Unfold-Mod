package pama1234.unfold.util;

import net.minecraft.util.math.Vec3d;

/**
 * 唯一的投影基（规格 A1）。
 *
 * 同时供相机角度、玩家输入映射、位置投影、LayerRaycaster、层滞回和目标位置重建使用。
 * 这里只保存几何定义，不做安全判定，也不读取世界。
 *
 * 依据：`Unfold后端与几何算法规格-2026-11-03.md` A1。
 * 除 {@link #ISO_PITCH_DEGREES} 的取值说明外，本类按规格的 [推断：几何定义] 实现，
 * 尚未在运行时验证其与相机实际成像完全一致。
 */
public final class ProjectionBasis {

    /**
     * 8 个斜角预设当前共用的俯仰角。
     *
     * 规格 A1 的精确值是 arctan(1/√2) ≈ 35.26438968°，但现有 ViewDirection 使用 35.26。
     * 本步骤只做统一，不改相机角度，因此保留原值。改用精确值会轻微改变相机朝向，
     * 需要先有运行时验证再决定（[推断]，未验证）。
     */
    public static final float ISO_PITCH_DEGREES = 35.26f;

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

        // 与旧 getUnfoldAxisDirection 相同的 Minecraft 相机前向公式。
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
        // [推断] 左右符号按规格的右手系取法；如需匹配相机左/右，要在运行时比对后调整。
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
     * 这是旧 projectPlayerToTargetY 的同一算式，用 λ 表达：
     * λ = (targetY - anchor.y) / d.y。
     *
     * 当 d.y 接近 0（水平模式不会调用本方法）时退回只改 Y，保持旧实现的退化行为。
     */
    public Vec3d reconstructAtY(Vec3d anchor, double targetY) {
        double axisY = axis.y;
        if (Math.abs(axisY) < 1.0e-6) {
            return new Vec3d(anchor.x, targetY, anchor.z);
        }
        double lambda = (targetY - anchor.y) / axisY;
        return reconstruct(anchor, lambda);
    }

    /** 屏幕坐标（右、上）。 */
    public record ScreenPos(double right, double up) {
    }
}
