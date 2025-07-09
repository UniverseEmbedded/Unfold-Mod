package pama1234.unfold.test.tasks;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import pama1234.unfold.test.TestRunner;
import pama1234.unfold.test.TestTask;

public class SetupSceneTask implements TestTask {

    private final MinecraftClient client = MinecraftClient.getInstance();
    private int tickCounter = 0;

    @Override
    public boolean run(TestRunner runner, ClientPlayerEntity player) {
        // 等待几帧确保命令执行完毕
        tickCounter++;
        return tickCounter > 5;
    }

    @Override
    public void onStart(TestRunner runner, ClientPlayerEntity player) {
        player.sendMessage(Text.literal("Setting up test environment..."), false);

        // 1. 禁用天气和时间更新
        client.getServer().getCommandManager().executeWithPrefix(client.getServer().getCommandSource(), "/gamerule doDaylightCycle false");
        client.getServer().getCommandManager().executeWithPrefix(client.getServer().getCommandSource(), "/gamerule doWeatherCycle false");
        client.getServer().getCommandManager().executeWithPrefix(client.getServer().getCommandSource(), "/time set noon");
        client.getServer().getCommandManager().executeWithPrefix(client.getServer().getCommandSource(), "/weather clear");

        // 2. 禁用怪物生成
        client.getServer().getCommandManager().executeWithPrefix(client.getServer().getCommandSource(), "/gamerule doMobSpawning false");

        // 3. 杀死所有非玩家实体
        client.getServer().getCommandManager().executeWithPrefix(client.getServer().getCommandSource(), "/kill @e[type=!minecraft:player]");

        player.sendMessage(Text.literal("Environment setup complete."), false);
    }

    @Override
    public String getDescription() {
        return "Setup Test Environment";
    }
}