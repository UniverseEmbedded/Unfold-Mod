package pama1234.unfold.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pama1234.unfold.logic.UnfoldManager;

/**
 * 服务端权威切层的第三种回执：位置包拉回。
 * 服务端的移动校验判定 moved wrongly / moved too quickly 后，
 * 会下发 PlayerPositionLookS2CPacket 把客户端拉回服务端位置。
 * 这里在包应用之后（TAIL）比较最终位置与在途预测：
 * 等于预测目标 -> 视为服务端接受；落在别处 -> 视为拉回，丢弃预测且不回写。
 */
@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {

    /** 与服务端权威位置比较的容差（格）。远小于任何一次拉回的位移。 */
    private static final double AUTHORITY_TOLERANCE = 1.0e-4;

    @Inject(method = "onPlayerPositionLook", at = @At("TAIL"))
    private void unfoldOnPlayerPositionLook(PlayerPositionLookS2CPacket packet, CallbackInfo ci) {
        Vec3d pending = UnfoldManager.pendingTeleportTarget;
        if (pending == null) {
            return;
        }
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null || player.hasVehicle()) {
            // 载具状态下 onPlayerPositionLook 不把位置写回玩家实体，没有可比对的权威位置。
            return;
        }
        // TAIL：位置包已经写入玩家实体。
        if (player.getPos().squaredDistanceTo(pending) <= AUTHORITY_TOLERANCE * AUTHORITY_TOLERANCE) {
            // 服务端权威位置就是预测目标：接受，位置保持不变。
            UnfoldManager.pendingTeleportTarget = null;
            UnfoldManager.pendingTeleportOrigin = null;
            UnfoldManager.pendingTeleportTicks = 0;
            return;
        }
        // 位置包落在别处：moved wrongly / moved too quickly 拉回，或其它服务端纠正。
        // 策略：丢弃预测，不回写目标位置（服务端位置已是权威值）；重置请求间隔冷却，
        // 但不改既有 teleportCooldown 的「落地即解除」语义。
        UnfoldManager.discardPendingTeleport();
    }
}
