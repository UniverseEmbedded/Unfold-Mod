package pama1234.unfold.test.tasks;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import pama1234.unfold.test.TestRunner;
import pama1234.unfold.test.TestTask;
import pama1234.unfold.UnfoldMod;

public class AssertBlockBelowTask implements TestTask {
    private final Block expectedBlock;
    private final String blockName;
    private boolean hasLogged = false;

    public AssertBlockBelowTask(Block expectedBlock, String blockName) {
        this.expectedBlock = expectedBlock;
        this.blockName = blockName;
    }

    @Override
    public boolean run(TestRunner runner, ClientPlayerEntity player) {
        // 获取玩家当前位置
        BlockPos playerPos = player.getBlockPos();

        // 检测玩家脚底的方块（玩家位置下方一格）
        BlockPos belowPos = playerPos.down();
        BlockState belowState = player.getWorld().getBlockState(belowPos);
        Block belowBlock = belowState.getBlock();

        // 详细的调试信息
        if (!hasLogged) {
            UnfoldMod.LOGGER.info("=== ASSERT BLOCK BELOW DEBUG ===");
            UnfoldMod.LOGGER.info("Player position: {}", playerPos);
            UnfoldMod.LOGGER.info("Position below: {}", belowPos);
            UnfoldMod.LOGGER.info("Expected block: {} ({})", expectedBlock.getTranslationKey(), blockName);
            UnfoldMod.LOGGER.info("Actual block: {}", belowBlock.getTranslationKey());
            UnfoldMod.LOGGER.info("Blocks equal: {}", belowBlock == expectedBlock);
            UnfoldMod.LOGGER.info("Is air: {}", belowState.isAir());
            UnfoldMod.LOGGER.info("Player velocity: {}", player.getVelocity());
            UnfoldMod.LOGGER.info("Player on ground: {}", player.isOnGround());
            UnfoldMod.LOGGER.info("================================");
            hasLogged = true;
        }

        // 如果是空气方块，说明场景可能还没有搭建完成
        if (belowState.isAir()) {
            throw new AssertionError(String.format(
                    "Player is standing on AIR at position %s. Expected %s block. Scene may not be built yet.",
                    belowPos, blockName
            ));
        }

        if (belowBlock != expectedBlock) {
            throw new AssertionError(String.format(
                    "Player should be standing on %s block, but found %s at position %s",
                    blockName,
                    belowBlock.getTranslationKey(),
                    belowPos
            ));
        }

        UnfoldMod.LOGGER.info("✓ Assert passed: Player is standing on {} block", blockName);
        return true;
    }

    @Override
    public void onStart(TestRunner runner, ClientPlayerEntity player) {
        hasLogged = false;
        UnfoldMod.LOGGER.info("Starting block assertion for: {}", blockName);
    }

    @Override
    public String getDescription() {
        return "Assert player is standing on " + blockName + " block";
    }
}