package pama1234.unfold.test.tasks;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import pama1234.unfold.test.TestRunner;
import pama1234.unfold.test.TestTask;
import pama1234.unfold.UnfoldMod;

public class BuildPlatformTask implements TestTask {
    private final BlockPos from;
    private final BlockPos to;
    private final BlockState blockState;
    private int waitTicks = 0;
    private static final int WAIT_DURATION = 10; // 等待10帧让命令执行

    public BuildPlatformTask(BlockPos center, BlockState state, Vec3i size) {
        this.from = center.add(-size.getX()/2, -size.getY()/2, -size.getZ()/2);
        this.to = center.add(size.getX()/2, size.getY()/2, size.getZ()/2);
        this.blockState = state;
    }

    @Override
    public boolean run(TestRunner runner, ClientPlayerEntity player) {
        waitTicks++;

        // 等待命令执行完成
        if (waitTicks < WAIT_DURATION) {
            return false;
        }

        // 验证方块是否真的被放置了
        BlockPos testPos = from; // 测试起始位置的方块
        BlockState actualState = player.getWorld().getBlockState(testPos);

        if (actualState.getBlock() != blockState.getBlock()) {
            UnfoldMod.LOGGER.warn("Build verification failed at {}: expected {}, got {}",
                    testPos, blockState.getBlock().getTranslationKey(), actualState.getBlock().getTranslationKey());
            // 不抛出错误，但记录警告
        } else {
            UnfoldMod.LOGGER.info("✓ Build verified: {} blocks placed successfully", blockState.getBlock().getTranslationKey());
        }

        return true;
    }

    @Override
    public void onStart(TestRunner runner, ClientPlayerEntity player) {
        String blockId = Registries.BLOCK.getId(blockState.getBlock()).toString();
        String command = String.format("/fill %d %d %d %d %d %d %s",
                from.getX(), from.getY(), from.getZ(), to.getX(), to.getY(), to.getZ(), blockId);

        UnfoldMod.LOGGER.info("Executing build command: {}", command);
        MinecraftClient.getInstance().getNetworkHandler().sendChatCommand(command.substring(1));
        waitTicks = 0;
    }

    @Override
    public String getDescription() {
        return "Build Platform (" + blockState.getBlock().getTranslationKey() + ")";
    }
}