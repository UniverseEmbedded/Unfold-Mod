package pama1234.unfold.test.tasks;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import pama1234.unfold.test.TestRunner;
import pama1234.unfold.test.TestTask;
import pama1234.unfold.UnfoldMod;

public class AssertBlockBelowTask implements TestTask {
    private final Block expectedBlock;
    private final String blockName;

    public AssertBlockBelowTask(Block expectedBlock, String blockName) {
        this.expectedBlock = expectedBlock;
        this.blockName = blockName;
    }

    @Override
    public boolean run(TestRunner runner, ClientPlayerEntity player) {
        // 获取玩家当前位置
        BlockPos playerPos = player.getBlockPos();
        BlockPos belowPos = playerPos.down();
        BlockState belowState = player.getWorld().getBlockState(belowPos);
        Block actualBlock = belowState.getBlock();

        // 调试信息
        UnfoldMod.LOGGER.info("=== ASSERT BLOCK BELOW ===");
        UnfoldMod.LOGGER.info("Player at: {}", playerPos);
        UnfoldMod.LOGGER.info("Checking: {}", belowPos);
        UnfoldMod.LOGGER.info("Expected: {} ({})", expectedBlock.getTranslationKey(), blockName);
        UnfoldMod.LOGGER.info("Actual: {}", actualBlock.getTranslationKey());
        UnfoldMod.LOGGER.info("=========================");

        // 检查空气
        if (belowState.isAir()) {
            String error = String.format("Player standing on AIR at %s, expected %s", belowPos, blockName);
            UnfoldMod.LOGGER.error("ASSERTION FAILED: {}", error);
            throw new AssertionError(error);
        }

        // 检查方块类型
        if (actualBlock != expectedBlock) {
            String error = String.format("Expected %s but found %s at %s",
                    blockName, actualBlock.getTranslationKey(), belowPos);
            UnfoldMod.LOGGER.error("ASSERTION FAILED: {}", error);
            throw new AssertionError(error);
        }

        // 成功
        UnfoldMod.LOGGER.info("✓ ASSERTION PASSED: Player standing on {}", blockName);
        return true;
    }

    @Override
    public void onStart(TestRunner runner, ClientPlayerEntity player) {
        UnfoldMod.LOGGER.info("Starting assertion: Player should be on {}", blockName);
    }

    @Override
    public String getDescription() {
        return "Assert player on " + blockName;
    }
}