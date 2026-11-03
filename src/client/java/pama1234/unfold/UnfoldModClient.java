package pama1234.unfold;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import pama1234.unfold.logic.TeleportTargetValidator;
import pama1234.unfold.logic.UnfoldConfig;
import pama1234.unfold.logic.UnfoldManager;
import pama1234.unfold.logic.UnfoldMode;
import pama1234.unfold.logic.UnfoldTeleportLogic;
import pama1234.unfold.network.TeleportResponsePayload;
import pama1234.unfold.util.ViewDirection;

public class UnfoldModClient implements ClientModInitializer {
    private static KeyBinding toggleKey;
    // [新增] 为相机缩放功能添加键位绑定
    private static KeyBinding zoomOutKey; // 对应键盘 '-' 键
    private static KeyBinding zoomInKey;  // 对应键盘 '=' 键 (通常是'+')

    private static Perspective originalPerspective;
    private static boolean teleportCooldown = false;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.unfold.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "category.unfold"
        ));

        // [新增] 注册缩放键位
        zoomOutKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.unfold.zoom_out", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_MINUS, "category.unfold"
        ));
        zoomInKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.unfold.zoom_in", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_EQUAL, "category.unfold"
        ));


        // 服务端回执：接受 / 拒绝（带具体原因）。拉回（moved wrongly / too quickly）由
        // ClientPlayNetworkHandlerMixin 观察权威位置包处理。
        ClientPlayNetworking.registerGlobalReceiver(TeleportResponsePayload.ID, (payload, context) ->
                context.client().execute(() -> handleTeleportResponse(payload.reason())));

        ClientTickEvents.END_CLIENT_TICK.register(tickClient -> {

            ClientPlayerEntity player = tickClient.player;
            if (player == null) {
                return;
            }

            handleModeToggle(tickClient, player);

            // [新增] 在展开模式下，处理缩放按键
            if (UnfoldManager.unfolding) {
                handleZoomKeys(tickClient);
            }

            if (UnfoldManager.unfolding) {
                // 服务端权威切层：请求间隔与在途预测维护。
                // 同一 tick 最多一次请求由 checkAndPerformTeleport 每 tick 只调一次保证；
                // 这里再叠加「上一请求未确认不重发」与 tick 间隔两道限流。
                if (UnfoldManager.teleportRequestCooldownTicks > 0) {
                    UnfoldManager.teleportRequestCooldownTicks--;
                }
                if (UnfoldManager.pendingTeleportTarget != null) {
                    UnfoldManager.pendingTeleportTicks++;
                    if (UnfoldManager.pendingTeleportTicks > UnfoldManager.PENDING_TELEPORT_TIMEOUT_TICKS) {
                        // 超时：丢弃预测，不回写目标位置；请求间隔冷却由 discard 重置。
                        UnfoldManager.discardPendingTeleport();
                    }
                }
                if (!teleportCooldown
                        && UnfoldManager.teleportRequestCooldownTicks == 0
                        && UnfoldManager.pendingTeleportTarget == null) {
                    if (UnfoldTeleportLogic.checkAndPerformTeleport(player)) {
                        teleportCooldown = true;
                    }
                } else {
                    // 卡点 4（既有行为，2025-07-09 提交 15e2d8f 起就是这样，本次不改）：
                    // 冷却在玩家落地时立即解除。水平模式切层后玩家仍在地面，因此下一 tick 就解除，
                    // 相邻两 tick 内可能再次触发切层。抑制同层正、反向往返的逻辑不在这里，
                    // 而在 UnfoldTeleportLogic.tryHorizontalCandidate / isReversedHorizontalCut。
                    if (teleportCooldown && player.isOnGround()) {
                        teleportCooldown = false;
                    }
                }
                handlePlayerMovement(tickClient, player);
            }

            // 规格 A4：记录本 tick 结束时的位置（含瞬移结果），供下一 tick 比较原版位移。
            UnfoldManager.lastTickPosition = player.getPos();
        });

    }

    /**
     * [新增] 处理相机缩放的按键输入
     * @param client 客户端实例
     */
    private void handleZoomKeys(MinecraftClient client) {
        boolean scaleChanged = false;
        // 检查缩小键 (通常是 '-')
        while (zoomOutKey.wasPressed()) {
            UnfoldConfig.changeScale(true); // true表示缩小
            scaleChanged = true;
        }
        // 检查放大键 (通常是 '=')
        while (zoomInKey.wasPressed()) {
            UnfoldConfig.changeScale(false); // false表示放大
            scaleChanged = true;
        }

        // 如果缩放被改变，向玩家发送提示信息，提供即时反馈
        if (scaleChanged && client.player != null) {
            String scaleText = String.format("%.2f", UnfoldConfig.scale);
            client.player.sendMessage(Text.translatable("unfold.message.scale_changed", scaleText), true);
        }
    }


    /**
     * 处理模式切换的逻辑。
     * 修正：在进入展开模式时，在聊天栏打印纯中文的模式和视角方向。
     *
     * @param client 客户端实例。
     * @param player 客户端玩家实体。
     */
    private void handleModeToggle(MinecraftClient client, ClientPlayerEntity player) {
        if (toggleKey.wasPressed()) {
            UnfoldManager.unfolding = !UnfoldManager.unfolding;

            if (UnfoldManager.unfolding) {
                UnfoldManager.currentDirection = ViewDirection.getClosest(player);
                UnfoldManager.currentMode = UnfoldMode.fromPlayerPitch(player.getPitch(), UnfoldManager.currentDirection);

                String modeName;
                switch (UnfoldManager.currentMode) {
                    case VERTICAL:
                        modeName = "垂直模式";
                        break;
                    case HORIZONTAL:
                        modeName = "水平模式";
                        break;
                    case ISOMETRIC:
                        modeName = "斜角模式";
                        break;
                    default:
                        modeName = "未知模式";
                        break;
                }
                String directionName = UnfoldManager.currentDirection.chineseName;
                String message = String.format("已进入: %s (视角: %s)", modeName, directionName);
                player.sendMessage(Text.literal(message), false);

                UnfoldMod.LOGGER.info("Unfold Mode Activated: {} (Direction: {})", UnfoldManager.currentMode, UnfoldManager.currentDirection.name());

                originalPerspective = null;
                if (UnfoldConfig.AUTO_THIRD_PERSON) {
                    originalPerspective = client.options.getPerspective();
                    client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
                }

            } else {
                UnfoldMod.LOGGER.info("Unfold Mode Deactivated");
                // 卡点 4：退出展开模式时清空水平切层记录，避免下次进入时误抑制一次正常切层。
                UnfoldManager.lastHorizontalCutBlock = null;
                UnfoldManager.lastHorizontalCutSign = 0.0;
                // 层滞回：退出时一并清空本次跳跃的换层方向，避免跨会话残留。
                UnfoldManager.jumpLayerDirection = 0;
                // 服务端权威切层：丢弃在途预测并清空请求间隔，避免跨会话残留。
                UnfoldManager.pendingTeleportTarget = null;
                UnfoldManager.pendingTeleportOrigin = null;
                UnfoldManager.pendingTeleportTicks = 0;
                UnfoldManager.teleportRequestCooldownTicks = 0;
                if (originalPerspective != null) {
                    client.options.setPerspective(originalPerspective);
                }
            }
        }
    }

    /**
     * 处理服务端对切层请求的回执，按拒绝原因决定本地位置处置。
     *
     * <ul>
     *   <li>{@code NONE}：服务端已执行 player.teleport，权威位置随后由 PlayerPositionLookS2CPacket 包落地；
     *       本地预测转为已确认，不再回写。</li>
     *   <li>{@code DISTANCE_EXCEEDED}、{@code COLLISION}：位置本身不可接受，回退到预测前位置，
     *       避免客户端停在越界或几何体内的未授权坐标上。</li>
     *   <li>{@code NO_SUPPORT}：预测位置没有碰撞，但没有支撑面（服务端层证据与客户端不一致）。
     *       保持预测位置，交给重力与随后的权威位置包纠正，不在同一 tick 内瞬移回 origin
     *       以免产生「来回跳」的视觉抖动。</li>
     * </ul>
     * 三种拒绝都会丢弃在途预测；请求间隔冷却由 discardPendingTeleport 重置。
     */
    private static void handleTeleportResponse(TeleportTargetValidator.RejectionReason reason) {
        if (UnfoldManager.pendingTeleportTarget == null) {
            // 过期回执（已超时或被拉回处理过）。
            return;
        }
        if (reason == TeleportTargetValidator.RejectionReason.NONE) {
            UnfoldManager.pendingTeleportTarget = null;
            UnfoldManager.pendingTeleportOrigin = null;
            UnfoldManager.pendingTeleportTicks = 0;
            return;
        }
        if (reason != TeleportTargetValidator.RejectionReason.NO_SUPPORT) {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            if (player != null && UnfoldManager.pendingTeleportOrigin != null) {
                Vec3d origin = UnfoldManager.pendingTeleportOrigin;
                player.setPos(origin.x, origin.y, origin.z);
            }
        }
        UnfoldManager.discardPendingTeleport();
    }

    private void handlePlayerMovement(MinecraftClient client, ClientPlayerEntity player) {
        if (UnfoldManager.currentMode == UnfoldMode.ISOMETRIC || UnfoldManager.currentMode == UnfoldMode.VERTICAL) {
            return;
        }

        if (UnfoldManager.currentMode == UnfoldMode.HORIZONTAL) {
            float strafeInput = 0.0f;
            if (client.options.leftKey.isPressed()) strafeInput -= 1.0f;
            if (client.options.rightKey.isPressed()) strafeInput += 1.0f;

            if (Math.abs(strafeInput) < 0.001f && player.isOnGround()) {
                player.setVelocity(0, player.getVelocity().y, 0);
                return;
            }

            Vec3d unfoldAxis = getUnfoldAxisDirection(UnfoldManager.currentDirection);
            Vec3d strafeAxis = unfoldAxis.crossProduct(new Vec3d(0, 1, 0)).normalize();

            Vec3d moveVec = strafeAxis.multiply(strafeInput * 0.2);
            player.setVelocity(moveVec.x, player.getVelocity().y, moveVec.z);
        }
    }

    private static Vec3d getUnfoldAxisDirection(ViewDirection viewDirection) {
        float yawRad = viewDirection.yaw * (float) (Math.PI / 180.0);
        float pitchRad = viewDirection.pitch * (float) (Math.PI / 180.0);
        double x = -Math.sin(yawRad) * Math.cos(pitchRad);
        double y = -Math.sin(pitchRad);
        double z = Math.cos(yawRad) * Math.cos(pitchRad);
        return new Vec3d(x, y, z).normalize();
    }
}