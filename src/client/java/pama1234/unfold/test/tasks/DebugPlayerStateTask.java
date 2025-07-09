package pama1234.unfold.test.tasks;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import pama1234.unfold.logic.UnfoldManager;
import pama1234.unfold.test.TestRunner;
import pama1234.unfold.test.TestTask;
import pama1234.unfold.UnfoldMod;

public class DebugPlayerStateTask implements TestTask {
    private final String label;

    public DebugPlayerStateTask(String label) {
        this.label = label;
    }

    @Override
    public boolean run(TestRunner runner, ClientPlayerEntity player) {
        BlockPos playerPos = player.getBlockPos();
        BlockPos belowPos = playerPos.down();

        UnfoldMod.LOGGER.info("=== DEBUG: {} ===", label);
        UnfoldMod.LOGGER.info("Player position: {}", playerPos);
        UnfoldMod.LOGGER.info("Block below: {}", player.getWorld().getBlockState(belowPos).getBlock().getTranslationKey());
        UnfoldMod.LOGGER.info("Player velocity: {}", player.getVelocity());
        UnfoldMod.LOGGER.info("Player on ground: {}", player.isOnGround());
        UnfoldMod.LOGGER.info("Unfold mode active: {}", UnfoldManager.unfolding);
        UnfoldMod.LOGGER.info("Current unfold mode: {}", UnfoldManager.currentMode);
        UnfoldMod.LOGGER.info("Current direction: {}", UnfoldManager.currentDirection);
        UnfoldMod.LOGGER.info("========================");

        return true;
    }

    @Override
    public void onStart(TestRunner runner, ClientPlayerEntity player) {
        // 立即执行调试
    }

    @Override
    public String getDescription() {
        return "Debug Player State: " + label;
    }
}