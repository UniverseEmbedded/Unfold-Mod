package pama1234.unfold.mixin.client;


import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import pama1234.unfold.logic.UnfoldManager; // <-- 导入 UnfoldManager

@Mixin(Camera.class)
public abstract class CameraMixin {

    @ModifyVariable(method = "moveBy", at = @At("HEAD"), index = 1, argsOnly = true)
    private float moveByHeadX(float value) {
        // 当展开模式开启时，将x轴的鼠标移动值设为0，禁用视角水平移动
        return UnfoldManager.unfolding ? 0.0F : value;
    }

    @ModifyVariable(method = "moveBy", at = @At("HEAD"), index = 3, argsOnly = true)
    private float moveByHeadZ(float value) {
        // 当展开模式开启时，将z轴（垂直）的鼠标移动值设为0，禁用视角垂直移动
        return UnfoldManager.unfolding ? 0.0F : value;
    }

}