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

    private Matrix4f createUnfoldOrthoMatrix() {
        // [修改] 从 UnfoldConfig.SCALE (常量) 改为 UnfoldConfig.scale (可变字段)
        float scale = UnfoldConfig.scale;
        float aspectRatio = (float) this.client.getWindow().getFramebufferWidth() / (float) this.client.getWindow().getFramebufferHeight();
        float width = scale * aspectRatio;
        float height = scale;
        // 使用 MIN_DISTANCE 和 MAX_DISTANCE 来确保渲染范围正确
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

    // 不再需要修改 Frustum 的矩阵，因为我们在 WorldRendererMixin 中直接跳过了 setupTerrain

    @ModifyExpressionValue(
            method = "renderWorld",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/joml/Quaternionf;conjugate(Lorg/joml/Quaternionf;)Lorg/joml/Quaternionf;"
            )
    )
    private Quaternionf modifyCameraRotation(Quaternionf original, @Local(argsOnly = true) RenderTickCounter tickCounter) {
        if (UnfoldManager.unfolding && this.client.player != null) {
            float targetYaw = UnfoldManager.currentDirection.yaw;
            float targetPitch = UnfoldManager.currentDirection.pitch;
            float pitchRad = targetPitch * MathHelper.RADIANS_PER_DEGREE;
            float yawRad = (targetYaw + 180.0f) * MathHelper.RADIANS_PER_DEGREE;
            return new Quaternionf().rotationXYZ(pitchRad, yawRad, 0.0f);
        }
        return original;
    }
}