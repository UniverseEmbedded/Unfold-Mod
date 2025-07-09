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

        // 检测玩家脚底的方块（玩家位置下方一格）
        BlockPos belowPos = playerPos.down();
        BlockState belowState = player.getWorld().getBlockState(belowPos);
        Block belowBlock = belowState.getBlock();

        if (belowBlock != expectedBlock) {
            throw new AssertionError(String.format(
                    "Player should be standing on %s block, but found %s at position %s",
                    blockName,
                    belowBlock.getTranslationKey(),
                    belowPos
            ));
        }

        return true;
    }

    @Override
    public void onStart(TestRunner runner, ClientPlayerEntity player) {
        // 可以在这里输出一些调试信息
        BlockPos playerPos = player.getBlockPos();
        BlockPos belowPos = playerPos.down();
        BlockState belowState = player.getWorld().getBlockState(belowPos);

        UnfoldMod.LOGGER.info("Checking block below player at {}: {}", belowPos, belowState.getBlock().getTranslationKey());
    }

    @Override
    public String getDescription() {
        return "Assert player is standing on " + blockName + " block";
    }
}