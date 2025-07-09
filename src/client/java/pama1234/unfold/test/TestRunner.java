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
        return Arrays.asList(
                // 诊断测试（用于验证测试系统本身）
                "diagnostic_simple_pass",
                "diagnostic_should_fail",
                "diagnostic_scene_building",

                // 原有的功能测试
                "horizontal_jump_across_debug",
                "horizontal_jump_across",
                "horizontal_walk_through_wall",
                "horizontal_jump_onto_platform",
                "horizontal_sneak_fall_behind",

                // 垂直展开测试
                "vertical_fall_down",
                "vertical_jump_up",

                // 斜向展开测试 (纪念碑谷风格)
                "isometric_walk_across_gap",
                "isometric_climb_stairs"
        );
    }

    private void buildTestByName(String name) {
        switch (name) {
            // 诊断测试
            case "diagnostic_simple_pass":
                buildDiagnosticSimplePassTest();
                break;
            case "diagnostic_should_fail":
                buildDiagnosticShouldFailTest();
                break;
            case "diagnostic_scene_building":
                buildDiagnosticSceneBuildingTest();
                break;
            case "horizontal_jump_across_debug":
                buildHorizontalJumpAcrossDebugTest();
                break;

            // 水平展开测试
            case "horizontal_jump_across":
                buildHorizontalJumpAcrossTest();
                break;
            case "horizontal_walk_through_wall":
                buildHorizontalWalkThroughWallTest();
                break;
            case "horizontal_jump_onto_platform":
                buildHorizontalJumpOntoPlatformTest();
                break;
            case "horizontal_sneak_fall_behind":
                buildHorizontalSneakFallBehindTest();
                break;

            // 垂直展开测试
            case "vertical_fall_down":
                buildVerticalFallDownTest();
                break;
            case "vertical_jump_up":
                buildVerticalJumpUpTest();
                break;

            // 斜向展开测试
            case "isometric_walk_across_gap":
                buildIsometricWalkAcrossGapTest();
                break;
            case "isometric_climb_stairs":
                buildIsometricClimbStairsTest();
                break;

            default:
                UnfoldMod.LOGGER.warn("Test case not found: {}", name);
                report.record(name, false, "Test case not found.");
        }
    }


// ==================== 诊断测试用例 ====================

    /**
     * 简单的通过测试 - 这个测试应该总是通过
     */
    private void buildDiagnosticSimplePassTest() {
        taskQueue.add(new LogTask("--- Diagnostic: Simple Pass Test ---"));

        // 1. 清理并搭建简单场景
        BlockPos center = new BlockPos(0, 64, 0);
        taskQueue.add(new ClearSceneTask(center, 10));
        taskQueue.add(new WaitTask(15)); // 充分等待

        // 2. 放置金块
        taskQueue.add(new LogTask("Placing gold block..."));
        taskQueue.add(new BuildPlatformTask(new BlockPos(0, 64, 0), Blocks.GOLD_BLOCK.getDefaultState(), new Vec3i(1, 1, 1)));
        taskQueue.add(new WaitTask(15)); // 充分等待

        // 3. 设置玩家位置
        taskQueue.add(new LogTask("Setting player position..."));
        taskQueue.add(new SetPlayerPosTask(new Vec3d(0.5, 65.0, 0.5)));
        taskQueue.add(new WaitTask(5));

        // 4. 调试状态
        taskQueue.add(new DebugPlayerStateTask("Before Simple Assert"));

        // 5. 断言 - 应该通过
        taskQueue.add(new LogTask("Performing assertion (should PASS)..."));
        taskQueue.add(new AssertBlockBelowTask(Blocks.GOLD_BLOCK, "Gold"));
    }

    /**
     * 故意失败的测试 - 这个测试应该总是失败
     */
    private void buildDiagnosticShouldFailTest() {
        taskQueue.add(new LogTask("--- Diagnostic: Should Fail Test ---"));

        // 1. 清理并搭建简单场景
        BlockPos center = new BlockPos(0, 64, 0);
        taskQueue.add(new ClearSceneTask(center, 10));
        taskQueue.add(new WaitTask(15));

        // 2. 放置石头块
        taskQueue.add(new LogTask("Placing stone block..."));
        taskQueue.add(new BuildPlatformTask(new BlockPos(0, 64, 0), Blocks.STONE.getDefaultState(), new Vec3i(1, 1, 1)));
        taskQueue.add(new WaitTask(15));

        // 3. 设置玩家位置
        taskQueue.add(new LogTask("Setting player position..."));
        taskQueue.add(new SetPlayerPosTask(new Vec3d(0.5, 65.0, 0.5)));
        taskQueue.add(new WaitTask(5));

        // 4. 调试状态
        taskQueue.add(new DebugPlayerStateTask("Before Failing Assert"));

        // 5. 断言 - 应该失败（期望金块但实际是石头）
        taskQueue.add(new LogTask("Performing assertion (should FAIL)..."));
        taskQueue.add(new AssertBlockBelowTask(Blocks.GOLD_BLOCK, "Gold"));
    }

    /**
     * 场景搭建测试 - 验证命令是否正确执行
     */
    private void buildDiagnosticSceneBuildingTest() {
        taskQueue.add(new LogTask("--- Diagnostic: Scene Building Test ---"));

        // 1. 清理
        BlockPos center = new BlockPos(0, 64, 0);
        taskQueue.add(new ClearSceneTask(center, 20));
        taskQueue.add(new WaitTask(20));

        // 2. 逐步搭建并验证每一步
        taskQueue.add(new LogTask("Building stone platform..."));
        taskQueue.add(new BuildPlatformTask(new BlockPos(0, 64, 0), Blocks.STONE.getDefaultState(), new Vec3i(3, 1, 3)));
        taskQueue.add(new WaitTask(15));

        // 验证石头平台
        taskQueue.add(new SetPlayerPosTask(new Vec3d(0.5, 65.0, 0.5)));
        taskQueue.add(new WaitTask(3));
        taskQueue.add(new DebugPlayerStateTask("After Stone Platform"));
        taskQueue.add(new AssertBlockBelowTask(Blocks.STONE, "Stone"));

        // 3. 添加金块平台
        taskQueue.add(new LogTask("Building gold platform..."));
        taskQueue.add(new BuildPlatformTask(new BlockPos(5, 65, 0), Blocks.GOLD_BLOCK.getDefaultState(), new Vec3i(3, 1, 3)));
        taskQueue.add(new WaitTask(15));

        // 移动到金块平台并验证
        taskQueue.add(new SetPlayerPosTask(new Vec3d(5.5, 66.0, 0.5)));
        taskQueue.add(new WaitTask(3));
        taskQueue.add(new DebugPlayerStateTask("After Gold Platform"));
        taskQueue.add(new AssertBlockBelowTask(Blocks.GOLD_BLOCK, "Gold"));

        taskQueue.add(new LogTask("Scene building test completed"));
    }

    /**
     * 调试版的横向跳跃测试
     */
    private void buildHorizontalJumpAcrossDebugTest() {
        taskQueue.add(new LogTask("--- Horizontal Jump Across Test (DEBUG VERSION) ---"));

        // 1. 场景搭建
        BlockPos center = new BlockPos(0, 64, 0);
        taskQueue.add(new ClearSceneTask(center, 30));
        taskQueue.add(new WaitTask(20)); // 更长的等待时间

        // 调试：检查清理后的状态
        taskQueue.add(new DebugPlayerStateTask("After Scene Clear"));

        // 起始平台在 X=0
        taskQueue.add(new LogTask("Building starting platform (stone)..."));
        taskQueue.add(new BuildPlatformTask(new BlockPos(0, 64, 0), Blocks.STONE.getDefaultState(), new Vec3i(1, 1, 5)));
        taskQueue.add(new WaitTask(15)); // 等待方块放置

        // 目标平台在 X=4 (横向空3格), Y=65 (高1格)
        taskQueue.add(new LogTask("Building target platform (gold)..."));
        taskQueue.add(new BuildPlatformTask(new BlockPos(4, 65, 0), Blocks.GOLD_BLOCK.getDefaultState(), new Vec3i(1, 1, 5)));
        taskQueue.add(new WaitTask(15)); // 等待方块放置

        // 调试：检查场景搭建后的状态
        taskQueue.add(new DebugPlayerStateTask("After Scene Build"));

        // 2. 玩家状态设置
        taskQueue.add(new LogTask("Setting player position on starting platform..."));
        taskQueue.add(new SetPlayerPosTask(new Vec3d(0.5, 65.0, 0.5)));
        taskQueue.add(new WaitTask(5)); // 等待位置设置

        // 验证玩家在起始平台
        taskQueue.add(new DebugPlayerStateTask("After Player Position Set"));
        taskQueue.add(new AssertBlockBelowTask(Blocks.STONE, "Stone")); // 应该在石头平台上

        // 使用 NEG_X 视角来消除 X 轴的深度
        taskQueue.add(new LogTask("Entering unfold mode..."));
        taskQueue.add(new EnterUnfoldModeTask(ViewDirection.NEG_X));
        taskQueue.add(new WaitTask(5)); // 等待模式切换

        // 调试：检查展开模式激活后的状态
        taskQueue.add(new DebugPlayerStateTask("After Unfold Mode Activated"));

        // 3. 动作与等待
        taskQueue.add(new LogTask("Starting movement simulation toward target..."));
        taskQueue.add(new SimulateMoveTask(new Vec3d(0.2, 0, 0), 5)); // 向+X移动，持续更长时间
        taskQueue.add(new WaitTask(5));

        // 调试：检查移动后的状态
        taskQueue.add(new DebugPlayerStateTask("After Movement"));

        taskQueue.add(new LogTask("Starting jump simulation..."));
        taskQueue.add(new SimulateJumpTask());
        taskQueue.add(new WaitTask(10)); // 观察跳跃过程

        // 调试：检查跳跃后的状态
        taskQueue.add(new DebugPlayerStateTask("After Jump"));

        taskQueue.add(new WaitTask(30)); // 等待瞬移和落地

        // 调试：检查最终状态
        taskQueue.add(new DebugPlayerStateTask("Final State Before Assert"));

        // 4. 最终结果断言
        taskQueue.add(new LogTask("Performing final assertion for gold block..."));
        taskQueue.add(new AssertBlockBelowTask(Blocks.GOLD_BLOCK, "Gold"));

        taskQueue.add(new LogTask("Debug test completed"));
    }

    // ==============================================
    // 水平展开测试 (FEZ风格)
    // ==============================================

    /**
     * 测试FEZ风格的视错觉跳跃：横向距离远，但视觉上相邻。
     */
    private void buildHorizontalJumpAcrossTest() {
        taskQueue.add(new LogTask("--- Starting Horizontal Jump Across Test ---"));

        // 1. 场景搭建
        BlockPos center = new BlockPos(0, 64, 0);
        taskQueue.add(new ClearSceneTask(center, 30));

        // 起始平台在 X=0
        taskQueue.add(new BuildPlatformTask(new BlockPos(0, 64, 0), Blocks.STONE.getDefaultState(), new Vec3i(1, 1, 5)));

        // 目标平台在 X=4 (横向空3格), Y=65 (高1格)
        taskQueue.add(new BuildPlatformTask(new BlockPos(4, 65, 0), Blocks.GOLD_BLOCK.getDefaultState(), new Vec3i(1, 1, 5)));

        // 2. 玩家状态设置
        taskQueue.add(new SetPlayerPosTask(new Vec3d(0.5, 65.0, 0.5)));

        // 使用 NEG_X 视角来消除 X 轴的深度
        taskQueue.add(new EnterUnfoldModeTask(ViewDirection.NEG_X));

        // 3. 动作与等待
        taskQueue.add(new SimulateMoveTask(new Vec3d(0.2, 0, 0), 2)); // 向+X移动
        taskQueue.add(new WaitTask(3));
        taskQueue.add(new SimulateJumpTask());
        taskQueue.add(new WaitTask(25)); // 等待跳跃、瞬移和落地

        // 4. 最终结果断言
        taskQueue.add(new AssertBlockBelowTask(Blocks.GOLD_BLOCK, "Gold"));
    }

    /**
     * 测试左右移动穿越障碍：走向墙壁时穿到另一侧。
     */
    private void buildHorizontalWalkThroughWallTest() {
        taskQueue.add(new LogTask("--- Starting Horizontal Walk Through Wall Test ---"));

        // 1. 场景搭建
        BlockPos center = new BlockPos(0, 64, 0);
        taskQueue.add(new ClearSceneTask(center, 30));

        // 起始平台
        taskQueue.add(new BuildPlatformTask(new BlockPos(0, 64, 0), Blocks.STONE.getDefaultState(), new Vec3i(1, 1, 5)));

        // 墙壁 (X=2, 高度2格)
        taskQueue.add(new BuildPlatformTask(new BlockPos(2, 65, 0), Blocks.COBBLESTONE.getDefaultState(), new Vec3i(1, 2, 5)));

        // 目标平台在墙壁后方 (X=4)
        taskQueue.add(new BuildPlatformTask(new BlockPos(4, 64, 0), Blocks.EMERALD_BLOCK.getDefaultState(), new Vec3i(1, 1, 5)));

        // 2. 玩家状态设置
        taskQueue.add(new SetPlayerPosTask(new Vec3d(0.5, 65.0, 0.5)));

        // 使用 NEG_X 视角
        taskQueue.add(new EnterUnfoldModeTask(ViewDirection.NEG_X));

        // 3. 动作：向墙壁走去
        taskQueue.add(new SimulateMoveTask(new Vec3d(0.3, 0, 0), 10)); // 持续向+X移动
        taskQueue.add(new WaitTask(20));

        // 4. 最终结果断言
        taskQueue.add(new AssertBlockBelowTask(Blocks.EMERALD_BLOCK, "Emerald"));
    }

    /**
     * 测试跳跃到障碍物上方：在障碍物下方跳跃，出现在顶部。
     */
    private void buildHorizontalJumpOntoPlatformTest() {
        taskQueue.add(new LogTask("--- Starting Horizontal Jump Onto Platform Test ---"));

        // 1. 场景搭建
        BlockPos center = new BlockPos(0, 64, 0);
        taskQueue.add(new ClearSceneTask(center, 30));

        // 起始平台 (低层)
        taskQueue.add(new BuildPlatformTask(new BlockPos(0, 64, 0), Blocks.STONE.getDefaultState(), new Vec3i(1, 1, 5)));

        // 目标平台 (高层, X=0, Y=67)
        taskQueue.add(new BuildPlatformTask(new BlockPos(0, 67, 0), Blocks.DIAMOND_BLOCK.getDefaultState(), new Vec3i(1, 1, 5)));

        // 2. 玩家状态设置
        taskQueue.add(new SetPlayerPosTask(new Vec3d(0.5, 65.0, 0.5)));

        // 使用 NEG_X 视角
        taskQueue.add(new EnterUnfoldModeTask(ViewDirection.NEG_X));

        // 3. 动作：跳跃
        taskQueue.add(new SimulateJumpTask());
        taskQueue.add(new WaitTask(20));

        // 4. 最终结果断言
        taskQueue.add(new AssertBlockBelowTask(Blocks.DIAMOND_BLOCK, "Diamond"));
    }

    /**
     * 测试从高台后方下落：在高台边缘潜行下落。
     */
    private void buildHorizontalSneakFallBehindTest() {
        taskQueue.add(new LogTask("--- Starting Horizontal Sneak Fall Behind Test ---"));

        // 1. 场景搭建
        BlockPos center = new BlockPos(0, 64, 0);
        taskQueue.add(new ClearSceneTask(center, 30));

        // 起始平台 (高层)
        taskQueue.add(new BuildPlatformTask(new BlockPos(0, 67, 0), Blocks.STONE.getDefaultState(), new Vec3i(1, 1, 5)));

        // 目标平台 (低层, X=2)
        taskQueue.add(new BuildPlatformTask(new BlockPos(2, 64, 0), Blocks.REDSTONE_BLOCK.getDefaultState(), new Vec3i(1, 1, 5)));

        // 2. 玩家状态设置
        taskQueue.add(new SetPlayerPosTask(new Vec3d(0.5, 68.0, 0.5)));

        // 使用 NEG_X 视角
        taskQueue.add(new EnterUnfoldModeTask(ViewDirection.NEG_X));

        // 3. 动作：潜行
        taskQueue.add(new SimulateSneakTask(10)); // 持续潜行10帧
        taskQueue.add(new WaitTask(20));

        // 4. 最终结果断言
        taskQueue.add(new AssertBlockBelowTask(Blocks.REDSTONE_BLOCK, "Redstone"));
    }

    // ==============================================
    // 垂直展开测试
    // ==============================================

    /**
     * 测试从高层平台落到视觉重叠的低层平台。
     */
    private void buildVerticalFallDownTest() {
        taskQueue.add(new LogTask("--- Starting Vertical Fall Down Test ---"));

        // 1. 场景搭建
        BlockPos center = new BlockPos(0, 64, 0);
        taskQueue.add(new ClearSceneTask(center, 30));

        // 起始平台 (高层)
        taskQueue.add(new BuildPlatformTask(new BlockPos(0, 70, 0), Blocks.STONE.getDefaultState(), new Vec3i(5, 1, 5)));

        // 目标平台 (低层, 视觉重叠)
        taskQueue.add(new BuildPlatformTask(new BlockPos(0, 64, 0), Blocks.LAPIS_BLOCK.getDefaultState(), new Vec3i(5, 1, 5)));

        // 2. 玩家状态设置
        taskQueue.add(new SetPlayerPosTask(new Vec3d(0.5, 71.0, 0.5)));

        // 使用垂直视角 (俯视)
        taskQueue.add(new EnterUnfoldModeTask(ViewDirection.POS_Y));

        // 3. 动作：走出平台边缘，触发下落
        taskQueue.add(new SimulateMoveTask(new Vec3d(0.3, 0, 0), 5));
        taskQueue.add(new WaitTask(25));

        // 4. 最终结果断言
        taskQueue.add(new AssertBlockBelowTask(Blocks.LAPIS_BLOCK, "Lapis"));
    }

    /**
     * 测试从低层平台跳到视觉重叠的高层平台。
     */
    private void buildVerticalJumpUpTest() {
        taskQueue.add(new LogTask("--- Starting Vertical Jump Up Test ---"));

        // 1. 场景搭建
        BlockPos center = new BlockPos(0, 64, 0);
        taskQueue.add(new ClearSceneTask(center, 30));

        // 起始平台 (低层)
        taskQueue.add(new BuildPlatformTask(new BlockPos(0, 64, 0), Blocks.STONE.getDefaultState(), new Vec3i(5, 1, 5)));

        // 目标平台 (高层, 视觉重叠)
        taskQueue.add(new BuildPlatformTask(new BlockPos(0, 70, 0), Blocks.IRON_BLOCK.getDefaultState(), new Vec3i(5, 1, 5)));

        // 2. 玩家状态设置
        taskQueue.add(new SetPlayerPosTask(new Vec3d(0.5, 65.0, 0.5)));

        // 使用垂直视角 (仰视)
        taskQueue.add(new EnterUnfoldModeTask(ViewDirection.NEG_Y));

        // 3. 动作：跳跃
        taskQueue.add(new SimulateJumpTask());
        taskQueue.add(new WaitTask(25));

        // 4. 最终结果断言
        taskQueue.add(new AssertBlockBelowTask(Blocks.IRON_BLOCK, "Iron"));
    }

    // ==============================================
    // 斜向展开测试 (纪念碑谷风格)
    // ==============================================

    /**
     * 测试在斜向模式下的自由行走和三维吸附。
     */
    private void buildIsometricWalkAcrossGapTest() {
        taskQueue.add(new LogTask("--- Starting Isometric Walk Across Gap Test ---"));

        // 1. 场景搭建
        BlockPos center = new BlockPos(0, 64, 0);
        taskQueue.add(new ClearSceneTask(center, 30));

        // 起始平台
        taskQueue.add(new BuildPlatformTask(new BlockPos(0, 64, 0), Blocks.STONE.getDefaultState(), new Vec3i(3, 1, 3)));

        // 目标平台 (斜向距离)
        taskQueue.add(new BuildPlatformTask(new BlockPos(4, 65, 4), Blocks.NETHERITE_BLOCK.getDefaultState(), new Vec3i(3, 1, 3)));

        // 2. 玩家状态设置
        taskQueue.add(new SetPlayerPosTask(new Vec3d(0.5, 65.0, 0.5)));

        // 使用斜向视角
        taskQueue.add(new EnterUnfoldModeTask(ViewDirection.ISO_POS_X_POS_Y));

        // 3. 动作：在二维平面上自由行走
        taskQueue.add(new SimulateMoveTask(new Vec3d(0.2, 0, 0.2), 15)); // 对角线移动
        taskQueue.add(new WaitTask(20));

        // 4. 最终结果断言
        taskQueue.add(new AssertBlockBelowTask(Blocks.NETHERITE_BLOCK, "Netherite"));
    }

    /**
     * 测试在斜向模式下的上升移动。
     */
    private void buildIsometricClimbStairsTest() {
        taskQueue.add(new LogTask("--- Starting Isometric Climb Stairs Test ---"));

        // 1. 场景搭建
        BlockPos center = new BlockPos(0, 64, 0);
        taskQueue.add(new ClearSceneTask(center, 30));

        // 起始平台
        taskQueue.add(new BuildPlatformTask(new BlockPos(0, 64, 0), Blocks.STONE.getDefaultState(), new Vec3i(2, 1, 2)));

        // 中间台阶
        taskQueue.add(new BuildPlatformTask(new BlockPos(2, 65, 2), Blocks.COBBLESTONE.getDefaultState(), new Vec3i(2, 1, 2)));

        // 目标平台 (更高)
        taskQueue.add(new BuildPlatformTask(new BlockPos(4, 67, 4), Blocks.COPPER_BLOCK.getDefaultState(), new Vec3i(2, 1, 2)));

        // 2. 玩家状态设置
        taskQueue.add(new SetPlayerPosTask(new Vec3d(0.5, 65.0, 0.5)));

        // 使用斜向视角
        taskQueue.add(new EnterUnfoldModeTask(ViewDirection.ISO_POS_X_POS_Y));

        // 3. 动作：分步移动到各个台阶
        taskQueue.add(new SimulateMoveTask(new Vec3d(0.1, 0, 0.1), 10)); // 到中间台阶
        taskQueue.add(new WaitTask(10));
        taskQueue.add(new SimulateMoveTask(new Vec3d(0.1, 0, 0.1), 10)); // 到目标平台
        taskQueue.add(new WaitTask(15));

        // 4. 最终结果断言
        taskQueue.add(new AssertBlockBelowTask(Blocks.COPPER_BLOCK, "Copper"));
    }
}