package pama1234.unfold.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
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
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import pama1234.unfold.logic.UnfoldManager;
import pama1234.unfold.util.ViewDirection;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Shadow @Final private MinecraftClient client;

    private Matrix4f createOrthoMatrix(float scale) {
        float width = scale * this.client.getWindow().getFramebufferWidth() / (float)this.client.getWindow().getFramebufferHeight();
        float height = scale;
        return new Matrix4f().setOrtho(-width, width, -height, height, 0.05F, 1000.0F);
    }

    @ModifyArg(
            method = "renderWorld",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;render(Lnet/minecraft/client/util/ObjectAllocator;Lnet/minecraft/client/render/RenderTickCounter;ZLnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/GameRenderer;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V"),
            index = 6
    )
    private Matrix4f modifyProjectionMatrix(Matrix4f projectionMatrix) {
        if (UnfoldManager.unfolding) {
            Matrix4f orthoMatrix = createOrthoMatrix(8.0f); // 固定的缩放值，可以后续做成可配置
            RenderSystem.setProjectionMatrix(orthoMatrix, ProjectionType.ORTHOGRAPHIC);
            return orthoMatrix;
        }
        return projectionMatrix;
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
            ClientPlayerEntity player = this.client.player;
            // 直接将 progress 设为 1.0f，表示瞬间完成过渡
            float progress = 1.0f;

            float targetYaw = UnfoldManager.currentDirection.yaw;
            float targetPitch = UnfoldManager.currentDirection.pitch;

            // 使用 player.getYaw() 和 player.getPitch() 获取当前玩家实际朝向，以便从当前朝向直接跳到目标朝向
            // 注意：这里不再使用 player.lastYaw/lastPitch，因为不再有平滑过渡，每次都从当前玩家视角直接跳转
            float snappedYaw = ViewDirection.lerpAngle(player.getYaw(), targetYaw, progress);
            float snappedPitch = MathHelper.lerp(progress, player.getPitch(), targetPitch);

            // 直接设置最终旋转，忽略玩家输入
            return new Quaternionf().rotationYXZ(
                    (snappedYaw + 180.0f) * MathHelper.RADIANS_PER_DEGREE,
                    -snappedPitch * MathHelper.RADIANS_PER_DEGREE,
                    0.0F
            );
        }
        return original;
    }
}