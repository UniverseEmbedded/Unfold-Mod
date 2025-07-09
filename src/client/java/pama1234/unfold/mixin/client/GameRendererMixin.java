package pama1234.unfold.mixin.client;

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
            at = @At(value = "INVOKE", target = "Lorg/joml/Quaternionf;conjugate(Lorg/joml/Quaternionf;)Lorg/joml/Quaternionf;")
    )
    private Quaternionf modifyCameraRotation(Quaternionf original, RenderTickCounter tickCounter) {
        if (UnfoldManager.unfolding && this.client.player != null) {
            ClientPlayerEntity player = this.client.player;
            float progress = UnfoldManager.getTransitionProgress(tickCounter.getTickDelta());

            float targetYaw = UnfoldManager.currentDirection.yaw;
            float targetPitch = UnfoldManager.currentDirection.pitch;

            // 平滑过渡
            float smoothedYaw = ViewDirection.lerpAngle(player.getPrevYaw(), targetYaw, progress);
            float smoothedPitch = MathHelper.lerp(progress, player.getPrevPitch(), targetPitch);

            // 直接设置最终旋转，忽略玩家输入
            return new Quaternionf().rotationYXZ(
                    (smoothedYaw + 180.0f) * MathHelper.RADIANS_PER_DEGREE,
                    -smoothedPitch * MathHelper.RADIANS_PER_DEGREE,
                    0.0F
            );
        }
        return original;
    }
}