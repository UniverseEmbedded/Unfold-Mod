package pama1234.unfold;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import pama1234.unfold.network.TeleportRequestPayload;

import java.util.logging.Logger;

public class UnfoldMod implements ModInitializer {

    public static final Logger LOGGER = Logger.getLogger("Unfold-Debug-main");

    @Override
    public void onInitialize() {
        // 注册Payload类型
        PayloadTypeRegistry.playC2S().register(TeleportRequestPayload.ID, TeleportRequestPayload.CODEC);

        // 注册服务器端的接收器
        ServerPlayNetworking.registerGlobalReceiver(TeleportRequestPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            Vec3d targetPos = payload.targetPos();

            // 服务端进行安全校验（例如，检查传送距离是否过远，防止作弊）
            // 256.0 是一个比较合理的上限
            if (player.getPos().distanceTo(targetPos) > 256.0) {
                return;
            }

            // 在服务器的主线程中执行传送
            player.getServer().execute(() -> {
                // 使用teleport，false表示不进行相对移动
                player.teleport(targetPos.x, targetPos.y, targetPos.z, false);
            });
        });
    }
}