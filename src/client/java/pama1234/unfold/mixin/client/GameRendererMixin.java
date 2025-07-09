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
import pama1234.unfold.UnfoldModClient;
import pama1234.unfold.logic.UnfoldConfig;
import pama1234.unfold.logic.UnfoldManager;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Shadow
    @Final
    private MinecraftClient client;

    private Matrix4f createUnfoldOrthoMatrix() {
        float scale = UnfoldConfig.SCALE;
        float aspectRatio = (float) this.client.getWindow().getFramebufferWidth() / (float) this.client.getWindow().getFramebufferHeight();
        float width = scale * aspectRatio;
        float height = scale;
        return new Matrix4f().setOrtho(-width, width, -height, height, UnfoldConfig.MIN_DISTANCE, UnfoldConfig.MAX_DISTANCE);
    }

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
    // 这里会报错 警告: Unable to locate method mapping for @At(INVOKE.<target>) 'Lorg/joml/Quaternionf;conjugate(Lorg/joml/Quaternionf;)Lorg/joml/Quaternionf;'
    // 但是实际上没有问题
    private Quaternionf modifyCameraRotation(Quaternionf original, @Local(argsOnly = true) RenderTickCounter tickCounter) {
        if (UnfoldManager.unfolding && this.client.player != null) {
            UnfoldModClient.LOGGER.info("[RENDER-ROT] Unfold mode is ON. Overriding camera rotation.");

            float targetYaw = UnfoldManager.currentDirection.yaw;
            float targetPitch = UnfoldManager.currentDirection.pitch;

            UnfoldModClient.LOGGER.info(String.format("[RENDER-ROT] Target Y/P: %.2f / %.2f", targetYaw, targetPitch));

            float pitchRad = targetPitch * MathHelper.RADIANS_PER_DEGREE;
            float yawRad = (targetYaw + 180.0f) * MathHelper.RADIANS_PER_DEGREE;

            UnfoldModClient.LOGGER.info(String.format("[RENDER-ROT] Calculated Rads P/Y: %.4f / %.4f", pitchRad, yawRad));
            UnfoldModClient.LOGGER.info("[RENDER-ROT] Original quaternion (being ignored): " + original);

            // 【【【 决定性修改 】】】
            // 不再修改传入的 original 对象。
            // 总是创建一个全新的、干净的四元数实例。
            // 这彻底切断了与上一帧状态的任何潜在反馈循环。
            Quaternionf newRotation = new Quaternionf().rotationXYZ(pitchRad, yawRad, 0.0f);

            UnfoldModClient.LOGGER.info("[RENDER-ROT] Returning new, clean quaternion: " + newRotation);
            return newRotation;
        }

        return original;
    }
}