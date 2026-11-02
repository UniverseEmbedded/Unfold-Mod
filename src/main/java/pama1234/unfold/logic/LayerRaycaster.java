package pama1234.unfold.logic;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 沿射线做方块 DDA 遍历，枚举每种 COLLIDER 形状的全部包围盒交点区间。
 *
 * 依据：`Unfold后端与几何算法规格-2026-11-03.md` A1/A3。
 * 本类只输出几何区间，不做候选搜索、不做安全判定、不读实体状态。
 *
 * [推断] 以下 API 签名按 Yarn 1.21.5 理解：`BlockState#getColliderShape()`、
 * `VoxelShape#getBoundingBoxes()`、`Box#offset(Vec3i)`。
 * 附件源码是 1.21.11 NeoForge，版本不一致，未编译验证。
 */
public final class LayerRaycaster {

    private static final double EPSILON = 1.0e-9;

    private LayerRaycaster() {
    }

    /**
     * 一个方块形状的命中区间。
     *
     * @param tEntry   进入区间的射线参数（0 表示起点已在形状内）
     * @param tExit    离开区间的射线参数
     * @param side     进入面的方向
     * @param blockPos 该形状所属方块
     * @param shape    该方块的 COLLIDER 形状
     */
    public record LayerInterval(double tEntry, double tExit, Direction side, BlockPos blockPos, VoxelShape shape) {
    }

    /** 从 start 到 end 发射一条射线，返回按 tEntry 升序排列的区间。 */
    public static List<LayerInterval> cast(BlockView world, Vec3d start, Vec3d end) {
        Vec3d delta = end.subtract(start);
        double length = delta.length();
        if (length < EPSILON) {
            return List.of();
        }
        return cast(world, start, delta.multiply(1.0 / length), length);
    }

    /**
     * 沿单位方向 direction 发射射线，最远 maxDistance。
     * direction 不要求预先归一化。
     */
    public static List<LayerInterval> cast(BlockView world, Vec3d start, Vec3d direction, double maxDistance) {
        Vec3d dir = direction.normalize();
        if (dir.lengthSquared() < EPSILON || maxDistance <= 0.0) {
            return List.of();
        }

        List<LayerInterval> result = new ArrayList<>();

        int cellX = MathHelper.floor(start.x);
        int cellY = MathHelper.floor(start.y);
        int cellZ = MathHelper.floor(start.z);

        int stepX = dir.x > EPSILON ? 1 : (dir.x < -EPSILON ? -1 : 0);
        int stepY = dir.y > EPSILON ? 1 : (dir.y < -EPSILON ? -1 : 0);
        int stepZ = dir.z > EPSILON ? 1 : (dir.z < -EPSILON ? -1 : 0);

        double tDeltaX = stepX == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dir.x);
        double tDeltaY = stepY == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dir.y);
        double tDeltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dir.z);

        double tMaxX = nextBoundary(start.x, cellX, stepX, dir.x);
        double tMaxY = nextBoundary(start.y, cellY, stepY, dir.y);
        double tMaxZ = nextBoundary(start.z, cellZ, stepZ, dir.z);

        double t = 0.0;
        while (t <= maxDistance) {
            BlockPos pos = new BlockPos(cellX, cellY, cellZ);
            BlockState state = world.getBlockState(pos);
            VoxelShape shape = state.getColliderShape();

            if (!shape.isEmpty()) {
                for (Box box : shape.getBoundingBoxes()) {
                    double[] interval = new double[2];
                    Direction side = intersect(start, dir, box.offset(pos), maxDistance, interval);
                    if (side != null) {
                        result.add(new LayerInterval(interval[0], interval[1], side, pos, shape));
                    }
                }
            }

            double tNext = Math.min(tMaxX, Math.min(tMaxY, tMaxZ));
            if (tNext > maxDistance) {
                break;
            }
            if (tMaxX <= tMaxY && tMaxX <= tMaxZ) {
                cellX += stepX;
                tMaxX += tDeltaX;
            } else if (tMaxY <= tMaxZ) {
                cellY += stepY;
                tMaxY += tDeltaY;
            } else {
                cellZ += stepZ;
                tMaxZ += tDeltaZ;
            }
            t = tNext;
        }

        result.sort(Comparator.comparingDouble(LayerInterval::tEntry));
        return result;
    }

    /** 当前格子到下一个格子边界的射线距离。 */
    private static double nextBoundary(double origin, int cell, int step, double dirComponent) {
        if (step == 0) {
            return Double.POSITIVE_INFINITY;
        }
        double boundary = step > 0 ? (cell + 1.0 - origin) : (origin - cell);
        return boundary / Math.abs(dirComponent);
    }

    /**
     * 射线与单个包围盒的 slab 求交。
     *
     * @return 进入面方向；未相交返回 null。起点已在盒内时返回与射线相反的面。
     */
    private static Direction intersect(Vec3d origin, Vec3d dir, Box box, double maxDistance, double[] out) {
        double tMin = 0.0;
        double tMax = maxDistance;
        Direction entrySide = null;

        if (Math.abs(dir.x) < EPSILON) {
            if (origin.x < box.minX || origin.x > box.maxX) {
                return null;
            }
        } else {
            double t1 = (box.minX - origin.x) / dir.x;
            double t2 = (box.maxX - origin.x) / dir.x;
            double near = Math.min(t1, t2);
            double far = Math.max(t1, t2);
            if (near > tMin) {
                tMin = near;
                entrySide = dir.x > 0 ? Direction.WEST : Direction.EAST;
            }
            tMax = Math.min(tMax, far);
            if (tMin > tMax) {
                return null;
            }
        }

        if (Math.abs(dir.y) < EPSILON) {
            if (origin.y < box.minY || origin.y > box.maxY) {
                return null;
            }
        } else {
            double t1 = (box.minY - origin.y) / dir.y;
            double t2 = (box.maxY - origin.y) / dir.y;
            double near = Math.min(t1, t2);
            double far = Math.max(t1, t2);
            if (near > tMin) {
                tMin = near;
                entrySide = dir.y > 0 ? Direction.DOWN : Direction.UP;
            }
            tMax = Math.min(tMax, far);
            if (tMin > tMax) {
                return null;
            }
        }

        if (Math.abs(dir.z) < EPSILON) {
            if (origin.z < box.minZ || origin.z > box.maxZ) {
                return null;
            }
        } else {
            double t1 = (box.minZ - origin.z) / dir.z;
            double t2 = (box.maxZ - origin.z) / dir.z;
            double near = Math.min(t1, t2);
            double far = Math.max(t1, t2);
            if (near > tMin) {
                tMin = near;
                entrySide = dir.z > 0 ? Direction.NORTH : Direction.SOUTH;
            }
            tMax = Math.min(tMax, far);
            if (tMin > tMax) {
                return null;
            }
        }

        if (tMax < 0.0) {
            return null;
        }

        out[0] = Math.max(tMin, 0.0);
        out[1] = Math.min(tMax, maxDistance);
        return entrySide != null ? entrySide : Direction.getFacing(-dir.x, -dir.y, -dir.z);
    }
}
