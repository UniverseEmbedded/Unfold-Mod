package pama1234.unfold;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pama1234.unfold.logic.TeleportTargetValidator;
import pama1234.unfold.network.TeleportRequestPayload;
import pama1234.unfold.network.TeleportResponsePayload;

public class UnfoldMod implements ModInitializer {

    public static final String MOD_ID = "unfold";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        // 注册Payload类型
        PayloadTypeRegistry.playC2S().register(TeleportRequestPayload.ID, TeleportRequestPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TeleportResponsePayload.ID, TeleportResponsePayload.CODEC);

        // 历史说明：这个接收器此前就已经注册，但客户端从未发送过 TeleportRequestPayload
        // （切层只有本地 setPos + 普通移动包）。从本次提交起，UnfoldTeleportLogic.teleportPlayer
        // 才在切层后真正发送请求，不要再把「服务端权威」当成早已实现。
        //
        // 终点校验与服务端授权：判定实现放在 common 侧 TeleportTargetValidator，
        // 与客户端预测的 isCandidateClear 第 1–7 步共用同一份代码与同一组常量，
        // 避免两端分叉。失败时回执带具体原因，客户端据此决定回退或保持。
        ServerPlayNetworking.registerGlobalReceiver(TeleportRequestPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            Vec3d targetPos = payload.targetPos();

            // 距离粗界：只拦明显不是切层的伪造请求，不是玩法约束。
            // 合法候选位移上界约 65 格（推导见 TeleportTargetValidator.MAX_REQUEST_DISTANCE
            // 与 docs/SPEC-UNFOLD-ALGORITHM.md 第 5、5.1 节），256 约为其 4 倍，
            // 因此该上限对正常切层不生效。
            if (player.getPos().distanceTo(targetPos) > TeleportTargetValidator.MAX_REQUEST_DISTANCE) {
                LOGGER.warn("Player {} teleport rejected: distance exceeded, from {} to {}",
                        player.getName().getString(), player.getPos(), targetPos);
                ServerPlayNetworking.send(player, new TeleportResponsePayload(
                        TeleportTargetValidator.RejectionReason.DISTANCE_EXCEEDED));
                return;
            }

            // 终点合法性（A5 第 1–7 步）：完整碰撞箱无碰撞、有支撑面、支撑面顶面 == 脚底 Y。
            // 服务端不做第 8 步 Π 复核：它没有展开方向，不应据此否决位置本身合法的落点。
            // 碰撞与支撑查询必须读世界状态，所以和 player.teleport 一起放到服务器主线程，
            // 不在网络线程上读取方块碰撞。
            player.getServer().execute(() -> {
                TeleportTargetValidator.RejectionReason reason = TeleportTargetValidator.validateEndpoint(
                        player.getServerWorld(), player, targetPos, targetPos.y);
                if (reason != TeleportTargetValidator.RejectionReason.NONE) {
                    LOGGER.warn("Player {} teleport rejected: {} at {}", player.getName().getString(), reason, targetPos);
                    ServerPlayNetworking.send(player, new TeleportResponsePayload(reason));
                    return;
                }
                // 使用teleport，false表示不进行相对移动
                player.teleport(targetPos.x, targetPos.y, targetPos.z, false);
                // 先落权威位置，再发接受回执：保证接受回执到达时服务端位置已经是目标点。
                ServerPlayNetworking.send(player, new TeleportResponsePayload(
                        TeleportTargetValidator.RejectionReason.NONE));
            });
        });

        LOGGER.info("Unfold Mod initialized.");
    }
}