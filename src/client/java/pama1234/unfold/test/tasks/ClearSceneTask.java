package pama1234.unfold.test.tasks;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import pama1234.unfold.test.TestRunner;
import pama1234.unfold.test.TestTask;
import pama1234.unfold.UnfoldMod;

public class ClearSceneTask implements TestTask {
    private final BlockPos center;
    private final int size;
    private int waitTicks = 0;
    private static final int WAIT_DURATION = 10; // 等待10帧让命令执行

    public ClearSceneTask(BlockPos center, int size) {
        this.center = center;
        this.size = size;
    }

    @Override
    public boolean run(TestRunner runner, ClientPlayerEntity player) {
        waitTicks++;
        return waitTicks >= WAIT_DURATION;
    }

    @Override
    public void onStart(TestRunner runner, ClientPlayerEntity player) {
        int half = size / 2;
        BlockPos from = center.add(-half, -half, -half);
        BlockPos to = center.add(half, half, half);

        String command = String.format("/fill %d %d %d %d %d %d minecraft:air",
                from.getX(), from.getY(), from.getZ(), to.getX(), to.getY(), to.getZ());

        UnfoldMod.LOGGER.info("Executing clear command: {}", command);
        MinecraftClient.getInstance().getNetworkHandler().sendChatCommand(command.substring(1));
        waitTicks = 0;
    }

    @Override
    public String getDescription() {
        return "Clear Scene";
    }
}