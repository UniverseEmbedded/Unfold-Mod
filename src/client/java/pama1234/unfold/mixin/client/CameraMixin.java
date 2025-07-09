package pama1234.unfold.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pama1234.unfold.logic.UnfoldManager;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    private float yaw;

    @Shadow
    private float pitch;

    @Shadow
    private Entity focusedEntity;

    /**
     * 禁用水平鼠标移动
     */
    @ModifyVariable(method = "moveBy", at = @At("HEAD"), index = 1, argsOnly = true)
    private float moveByHeadX(float value) {
        return UnfoldManager.unfolding ? 0.0F : value;
    }

    /**
     * 禁用垂直鼠标移动
     */
    @ModifyVariable(method = "moveBy", at = @At("HEAD"), index = 3, argsOnly = true)
    private float moveByHeadZ(float value) {
        return UnfoldManager.unfolding ? 0.0F : value;
    }

    /**
     * 在展开模式下强制设置摄像机角度
     * 这确保了摄像机始终锁定在正确的展开角度
     */
    @Inject(method = "update", at = @At("TAIL"))
    private void unfold_forceCamera(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
        if (UnfoldManager.unfolding && UnfoldManager.currentDirection != null) {
            // 强制设置摄像机到预设的展开角度
            this.yaw = UnfoldManager.currentDirection.yaw;
            this.pitch = UnfoldManager.currentDirection.pitch;
        }
    }

    /**
     * 防止摄像机在展开模式下被其他系统修改角度
     */
    @Inject(method = "setRotation", at = @At("HEAD"), cancellable = true)
    private void unfold_preventRotationChange(float yaw, float pitch, CallbackInfo ci) {
        if (UnfoldManager.unfolding) {
            // 在展开模式下，阻止任何外部的角度修改
            ci.cancel();
        }
    }
}