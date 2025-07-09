package pama1234.unfold.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import pama1234.unfold.test.TestRunner;

import static net.minecraft.server.command.CommandManager.*;

public class TestCommand {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            register(dispatcher);
        });
    }

    private static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("unfold")
                .then(literal("test")
                        .executes(context -> {
                            context.getSource().sendFeedback(() -> Text.literal("请指定测试名称或使用 'all'"), false);
                            return 1;
                        })
                        .then(argument("test_name", StringArgumentType.string())
                                .suggests((context, builder) -> {
                                    builder.suggest("all");
                                    // 在这里可以添加所有测试用例的名称
                                    TestRunner.getTestNames().forEach(builder::suggest);
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    String testName = StringArgumentType.getString(context, "test_name");
                                    // 将测试请求发送到客户端的 TestRunner
                                    // 注意：这里只是一个触发机制，真正的测试逻辑在客户端
                                    context.getSource().sendFeedback(() -> Text.literal("正在客户端启动测试: " + testName), false);
                                    TestRunner.INSTANCE.startTest(testName);
                                    return 1;
                                })
                        )
                )
        );
    }
}