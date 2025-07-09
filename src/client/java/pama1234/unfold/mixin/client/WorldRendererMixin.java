package pama1234.unfold.mixin.client;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pama1234.unfold.logic.UnfoldManager;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin {

    @Inject(method = "setupTerrain", at = @At("HEAD"), cancellable = true)
    private void unfold_skipTerrainSetup(Camera camera, Frustum frustum, boolean hasForcedFrustum, int frame, boolean spectator, CallbackInfo ci) {
        if (UnfoldManager.unfolding) {
            // 在Unfold模式下，完全跳过 setupTerrain 的所有逻辑。
            // 这会禁用视锥体剔除，但能从根源上避免死循环。
            ci.cancel();
        }
    }
}