package pama1234.unfold.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import pama1234.unfold.logic.TeleportTargetValidator;

/**
 * 服务端对 {@link TeleportRequestPayload} 的显式回执。
 *
 * <p>不再只回一个布尔：失败时带上 {@link TeleportTargetValidator.RejectionReason}
 * （距离超限 / 无支撑 / 有碰撞），客户端按原因决定回退到 origin 还是保持预测位置。
 * {@link TeleportTargetValidator.RejectionReason#NONE} 表示接受，服务端已执行 player.teleport，
 * 客户端随后还会收到权威位置包（PlayerPositionLookS2CPacket）。
 *
 * <p>协议只传枚举序号（varint）：序号顺序即协议的一部分，调整
 * {@link TeleportTargetValidator.RejectionReason} 的声明顺序会改变线上格式，
 * 因此枚举只允许在末尾追加。读取端对越界序号做了边界检查，
 * 不把来自网络的任意整数直接当数组下标。
 */
public record TeleportResponsePayload(TeleportTargetValidator.RejectionReason reason) implements CustomPayload {
    public static final CustomPayload.Id<TeleportResponsePayload> ID = new CustomPayload.Id<>(Identifier.of("unfold", "teleport_response"));
    public static final PacketCodec<RegistryByteBuf, TeleportResponsePayload> CODEC = PacketCodec.of(
            (value, buf) -> buf.writeVarInt(value.reason.ordinal()),
            (buf) -> new TeleportResponsePayload(decodeReason(buf.readVarInt()))
    );

    /** 接受回执的判定入口：只有 {@link TeleportTargetValidator.RejectionReason#NONE} 表示服务端已落位。 */
    public boolean accepted() {
        return reason == TeleportTargetValidator.RejectionReason.NONE;
    }

    private static TeleportTargetValidator.RejectionReason decodeReason(int ordinal) {
        TeleportTargetValidator.RejectionReason[] values = TeleportTargetValidator.RejectionReason.values();
        if (ordinal < 0 || ordinal >= values.length) {
            // 越界序号按「有碰撞」处理：客户端会回退到预测前位置，不会停在未授权坐标上。
            return TeleportTargetValidator.RejectionReason.COLLISION;
        }
        return values[ordinal];
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
