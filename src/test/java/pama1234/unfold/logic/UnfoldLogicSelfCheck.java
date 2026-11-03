package pama1234.unfold.logic;

import net.minecraft.util.math.Vec3d;
import pama1234.unfold.util.ProjectionBasis;

/**
 * 第一批逻辑自检：不启动游戏、不依赖 JUnit，用普通 main 跑三条核心用例。
 *
 * 覆盖：垂直/斜向切层的 λ 反解与 Π 复核、水平切层（d⊥Y）产出真实候选、Π 不变量。
 * 运行：gradlew selfCheck
 */
public final class UnfoldLogicSelfCheck {

    private static int failures = 0;

    public static void main(String[] args) {
        verticalLayerCut();
        horizontalLayerCut();
        projectionInvariant();

        if (failures > 0) {
            System.out.println("FAILED: " + failures + " check(s)");
            System.exit(1);
        }
        System.out.println("ALL PASS");
    }

    /** 用例 1：垂直/斜向切层，λ 反解与 Π 复核。 */
    private static void verticalLayerCut() {
        ProjectionBasis basis = ProjectionBasis.fromAngles(45.0f, -ProjectionBasis.ISO_PITCH_DEGREES);
        Vec3d anchor = new Vec3d(0.0, 64.0, 0.0);
        double targetY = 65.0;

        check("垂直切层：canChangeWorldY 为 true", basis.canChangeWorldY());
        Vec3d candidate = basis.reconstructAtY(anchor, targetY);
        check("垂直切层：候选世界 Y 等于目标 Y", Math.abs(candidate.y - targetY) < 1.0e-9);
        double expectedLambda = (targetY - anchor.y) / basis.axis().y;
        check("垂直切层：λ 反解与 (targetY-anchor.y)/d.y 一致",
                Math.abs(basis.lambdaFor(anchor, candidate) - expectedLambda) < 1.0e-9);
        check("垂直切层：Π(candidate) == Π(anchor)",
                basis.preservesProjection(anchor, candidate, 1.0e-6));
    }

    /** 用例 2：水平切层 d⊥Y，λ 由层区间给出时产出真实候选，而不是原地不动。 */
    private static void horizontalLayerCut() {
        ProjectionBasis basis = ProjectionBasis.fromAngles(90.0f, 0.0f);
        Vec3d anchor = new Vec3d(0.0, 64.0, 0.0);

        check("水平切层：canChangeWorldY 为 false", !basis.canChangeWorldY());

        Vec3d candidate = basis.reconstruct(anchor, 5.0);
        check("水平切层：世界 Y 不变", Math.abs(candidate.y - anchor.y) < 1.0e-9);
        check("水平切层：候选不是原地（λ≠0）", candidate.squaredDistanceTo(anchor) > 1.0e-6);
        check("水平切层：Π(candidate) == Π(anchor)",
                basis.preservesProjection(anchor, candidate, 1.0e-6));
        check("水平切层：reconstructAtY 在 d⊥Y 下退化为原地",
                basis.reconstructAtY(anchor, 65.0).squaredDistanceTo(anchor) < 1.0e-9);
    }

    /** 用例 3：Π 复核在 1e-6 容差下的正例与反例。 */
    private static void projectionInvariant() {
        ProjectionBasis basis = ProjectionBasis.fromAngles(45.0f, -ProjectionBasis.ISO_PITCH_DEGREES);
        Vec3d anchor = new Vec3d(1.5, 70.0, -2.5);

        Vec3d onAxis = basis.reconstruct(anchor, 12.0);
        check("Π 复核：沿 d 的重建通过", basis.preservesProjection(anchor, onAxis, 1.0e-6));

        Vec3d offAxis = onAxis.add(basis.screenRight().multiply(0.01));
        check("Π 复核：屏幕方向偏移 0.01 被拒绝",
                !basis.preservesProjection(anchor, offAxis, 1.0e-6));
    }

    private static void check(String name, boolean ok) {
        if (ok) {
            System.out.println("PASS " + name);
        } else {
            failures++;
            System.out.println("FAIL " + name);
        }
    }
}
