package pama1234.unfold.test.tasks;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import pama1234.unfold.test.TestRunner;
import pama1234.unfold.test.TestTask;

public class ClearSceneTask implements TestTask {
    private final BlockPos center;
    private final int size;

    public ClearSceneTask(BlockPos center, int size) {
        this.center = center;
        this.size = size;
    }

    @Override
    public boolean run(TestRunner runner, ClientPlayerEntity player) {
        return true;
    }

    @Override
    public void onStart(TestRunner runner, ClientPlayerEntity player) {
        int half = size / 2;
        BlockPos from = center.add(-half, -half, -half);
        BlockPos to = center.add(half, half, half);
        String command = String.format("/fill %d %d %d %d %d %d minecraft:air",
                from.getX(), from.getY(), from.getZ(), to.getX(), to.getY(), to.getZ());
        MinecraftClient.getInstance().getNetworkHandler().sendChatCommand(command.substring(1));
    }

    @Override
    public String getDescription() {
        return "Clear Scene";
    }
}