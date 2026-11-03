package pama1234.unfold;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
        ServerPlayNetworking.registerGlobalReceiver(TeleportRequestPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            Vec3d targetPos = payload.targetPos();

            // 服务端进行安全校验（例如，检查传送距离是否过远，防止作弊）
            // 256.0 是一个比较合理的上限
            if (player.getPos().distanceTo(targetPos) > 256.0) {
                LOGGER.warn("Player {} tried to teleport too far, from {} to {}", player.getName().getString(), player.getPos(), targetPos);
                // 显式拒绝回执：客户端据此丢弃预测并回退到预测前位置。
                ServerPlayNetworking.send(player, new TeleportResponsePayload(false));
                return;
            }

            // 在服务器的主线程中执行传送
            player.getServer().execute(() -> {
                // 使用teleport，false表示不进行相对移动
                player.teleport(targetPos.x, targetPos.y, targetPos.z, false);
                // 先落权威位置，再发接受回执：保证接受回执到达时服务端位置已经是目标点。
                ServerPlayNetworking.send(player, new TeleportResponsePayload(true));
            });
        });

        LOGGER.info("Unfold Mod initialized.");
    }
}