package pama1234.unfold.test;

import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import pama1234.unfold.UnfoldMod;
import pama1234.unfold.test.tasks.*;
import pama1234.unfold.util.ViewDirection;

import java.util.*;

public class TestRunner {
    public static final TestRunner INSTANCE = new TestRunner();
    private final MinecraftClient client = MinecraftClient.getInstance();
    private final Queue<TestTask> taskQueue = new LinkedList<>();
    private final TestReport report = new TestReport();

    private boolean isRunning = false;
    private TestTask currentTask = null;
    private String currentTestCaseName = "";
    private Queue<String> testNamesToRun = new LinkedList<>();


    private TestRunner() {}

    public void startTest(String testName) {
        if (isRunning) {
            client.player.sendMessage(Text.literal("A test is already running."), false);
            return;
        }

        if ("all".equalsIgnoreCase(testName)) {
            this.testNamesToRun = new LinkedList<>(getTestNames());
        } else {
            this.testNamesToRun = new LinkedList<>(Collections.singletonList(testName));
        }

        this.isRunning = true;
        this.currentTask = null;
        UnfoldMod.LOGGER.info("====== Starting Test Sequence ======");

        buildNextTestCase();
    }

    public void tick() {
        if (!isRunning) return;

        ClientPlayerEntity player = client.player;
        if (player == null) {
            stopTest(true);
            return;
        }

        if (currentTask == null) {
            if (taskQueue.isEmpty()) {
                if (!currentTestCaseName.isEmpty()) {
                    report.record(currentTestCaseName, true, "All steps passed.");
                    currentTestCaseName = "";
                }
                buildNextTestCase();
                return;
            }

            currentTask = taskQueue.poll();
            UnfoldMod.LOGGER.info("[TEST] Running task: {}", currentTask.getDescription());
            currentTask.onStart(this, player);
        }

        try {
            if (currentTask.run(this, player)) {
                currentTask = null;
            }
        } catch (AssertionError e) {
            report.record(currentTestCaseName, false, "Failed at step [" + currentTask.getDescription() + "]: " + e.getMessage());
            taskQueue.clear();
            currentTask = null;
        }
    }

    private void buildNextTestCase() {
        if (testNamesToRun.isEmpty()) {
            stopTest(false);
            return;
        }
        String nextTestName = testNamesToRun.poll();
        this.currentTestCaseName = nextTestName;

        taskQueue.add(new SetupSceneTask());
        buildTestByName(nextTestName);
    }

    private void stopTest(boolean aborted) {
        isRunning = false;
        currentTask = null;
        taskQueue.clear();
        testNamesToRun.clear();

        if (aborted) {
            UnfoldMod.LOGGER.error("====== Test Sequence Aborted! ======");
        } else {
            UnfoldMod.LOGGER.info("====== Test Sequence Finished ======");
        }

        report.generate();

        if (client.player != null) {
            client.player.sendMessage(Text.literal("Test sequence finished. See console/report file for results."), false);
        }
    }

    // --- 测试用例构造 ---

    public static List<String> getTestNames() {
        // 【已更新】测试用例名称
        return new ArrayList<>(Collections.singletonList("horizontal_jump_across"));
    }

    private void buildTestByName(String name) {
        if ("horizontal_jump_across".equals(name)) {
            buildHorizontalJumpAcrossTest();
        } else {
            UnfoldMod.LOGGER.warn("Test case not found: {}", name);
            report.record(name, false, "Test case not found.");
        }
    }

    /**
     * 【全新，正确的测试用例】
     * 测试FEZ风格的视错觉跳跃：横向距离远，但视觉上相邻。
     */
    private void buildHorizontalJumpAcrossTest() {
        taskQueue.add(new LogTask("--- Starting Horizontal Jump Across Test (View +Z) ---"));

        // 1. 场景搭建
        BlockPos center = new BlockPos(0, 64, 0);
        taskQueue.add(new ClearSceneTask(center, 30));
        // 起始平台在 X=0
        taskQueue.add(new BuildPlatformTask(new BlockPos(0, 64, 0), Blocks.STONE.getDefaultState(), new Vec3i(1, 1, 5)));
        // 目标平台在 X=4 (横向空3格), Y=65 (高1格)
        taskQueue.add(new BuildPlatformTask(new BlockPos(4, 65, 0), Blocks.GOLD_BLOCK.getDefaultState(), new Vec3i(1, 1, 5)));

        // 2. 玩家状态设置
        taskQueue.add(new SetPlayerPosTask(new Vec3d(0.5, 65.0, 0.5)));
        // 关键：使用 +Z 视角来消除 X 轴的深度
        taskQueue.add(new EnterUnfoldModeTask(ViewDirection.NEG_X));

        // 3. 动作与等待
        // 模拟玩家向右(+X)走一小步，然后跳跃
        // 这会让 checkHorizontalAirStrafe 中的 velocity.normalize() 得到正确的方向
        taskQueue.add(new SimulateMoveTask(new Vec3d(0.2, 0, 0), 2)); // 短暂施加速率
        taskQueue.add(new WaitTask(3));
        taskQueue.add(new SimulateJumpTask());
        taskQueue.add(new WaitTask(25)); // 等待跳跃、瞬移和落地

        // 4. 最终结果断言
        // 玩家应该落在黄金平台上
        taskQueue.add(new AssertPositionTask(new BlockPos(4, 66, 0), 1.5));
    }
}