package pama1234.unfold.test.tasks;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import pama1234.unfold.test.TestRunner;
import pama1234.unfold.test.TestTask;

public class AssertPositionTask implements TestTask {
    private final BlockPos expectedPos;
    private final double tolerance;

    public AssertPositionTask(BlockPos expectedPos, double tolerance) {
        this.expectedPos = expectedPos;
        this.tolerance = tolerance;
    }

    @Override
    public boolean run(TestRunner runner, ClientPlayerEntity player) {
        double distance = player.getBlockPos().getSquaredDistance(expectedPos);
        if (distance > tolerance * tolerance) {
            throw new AssertionError(String.format("Player position assertion failed. Expected: %s, Actual: %s",
                    expectedPos, player.getBlockPos()));
        }
        return true;
    }

    @Override
    public void onStart(TestRunner runner, ClientPlayerEntity player) {
    }

    @Override
    public String getDescription() {
        return "Assert Player Position";
    }
}