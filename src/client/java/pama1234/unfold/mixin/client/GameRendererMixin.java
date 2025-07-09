package pama1234.unfold.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import pama1234.unfold.logic.UnfoldConfig;
import pama1234.unfold.logic.UnfoldManager;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Shadow
    @Final
    private MinecraftClient client;

    // --- 抽取出一个独立的、可复用的矩阵创建方法 ---
    private Matrix4f createUnfoldOrthoMatrix() {
        // 使用 UnfoldConfig 中的参数
        float scale = UnfoldConfig.SCALE;
        float aspectRatio = (float) this.client.getWindow().getFramebufferWidth() / (float) this.client.getWindow().getFramebufferHeight();
        float width = scale * aspectRatio;
        float height = scale;

        // 【关键】使用正确的近/远裁剪面值
        return new Matrix4f().setOrtho(-width, width, -height, height, UnfoldConfig.MIN_DISTANCE, UnfoldConfig.MAX_DISTANCE);
    }

    // --- 修改点1: 最终渲染矩阵 ---
    @ModifyArg(
            method = "renderWorld",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;render(Lnet/minecraft/client/util/ObjectAllocator;Lnet/minecraft/client/render/RenderTickCounter;ZLnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/GameRenderer;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V"),
            index = 6
    )
    private Matrix4f modifyProjectionMatrixForRendering(Matrix4f projectionMatrix) {
        if (UnfoldManager.unfolding) {
            Matrix4f orthoMatrix = createUnfoldOrthoMatrix();
            RenderSystem.setProjectionMatrix(orthoMatrix, ProjectionType.ORTHOGRAPHIC);
            return orthoMatrix;
        }
        return projectionMatrix;
    }

    // --- 修改点2: 视锥体剔除矩阵 ---
    @ModifyArg(
            method = "renderWorld",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/WorldRenderer;setupFrustum(Lnet/minecraft/util/math/Vec3d;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V"
            ),
            index = 2
    )
    private Matrix4f modifyProjectionMatrixForFrustum(Matrix4f projMat) {
        if (UnfoldManager.unfolding) {
            // 【重要】确保这里返回和上面完全一样的矩阵
            return createUnfoldOrthoMatrix();
        }
        return projMat;
    }


    @ModifyExpressionValue(
            method = "renderWorld",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/joml/Quaternionf;conjugate(Lorg/joml/Quaternionf;)Lorg/joml/Quaternionf;"
            )
    )
    private Quaternionf modifyCameraRotation(Quaternionf original, @Local(argsOnly = true) RenderTickCounter tickCounter) {
        if (UnfoldManager.unfolding && this.client.player != null) {
            // 我们不再需要插值了，因为是瞬间切换
            float targetYaw = UnfoldManager.currentDirection.yaw;
            float targetPitch = UnfoldManager.currentDirection.pitch;

            // 【关键修改】严格按照 OrthoCamera 的方式构建四元数
            // 1. 使用 rotationXYZ 方法
            // 2. 第一个参数是 Pitch
            // 3. 第二个参数是 Yaw，并且需要调整180度
            // 4. 第三个参数是 Roll，为 0

            // 注意：我们直接使用 targetPitch，不需要取反。OrthoCamera 也没有取反。
            float pitchRad = targetPitch * MathHelper.RADIANS_PER_DEGREE;

            // OrthoCamera 使用 yaw_in_rad - PI。我们使用 yaw_in_deg + 180 再转弧度，效果一样。
            float yawRad = (targetYaw + 180.0f) * MathHelper.RADIANS_PER_DEGREE;

            // 这里不再使用 `new Quaternionf()...` 而是直接修改 `original` 对象
            // 这也是 OrthoCamera 的做法，更加高效，避免了不必要的对象创建。
            return original.rotationXYZ(pitchRad, yawRad, 0.0f);
        }
        return original;
    }
}