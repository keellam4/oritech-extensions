package io.github.keellam4.oritechextensions.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Where each slot sits on a vault's front, in block units seen from the front (u to the right, v down, 0 to 1).
 * Matches the textures from tools/storage/cargo_vault_textures.py.
 */
public final class VaultLayout {

    /** One slot's window: the glass the item floats in and the strip that shows how full it is. */
    public record Cell(float glassU0, float glassV0, float glassU1, float glassV1,
                       float gaugeU0, float gaugeV0, float gaugeU1, float gaugeV1, int gaugePixels) {
    }

    private VaultLayout() {
    }

    /** Cells for a vault with {@code perSide} × {@code perSide} slots, in slot order (left to right, top to bottom). */
    public static Cell[] cells(int perSide) {
        if (perSide == 1) {
            return new Cell[]{new Cell(3 / 16f, 4 / 16f, 13 / 16f, 12 / 16f, 3 / 16f, 3 / 16f, 13 / 16f, 4 / 16f, 10)};
        }
        // Quad (16 px) and Grid (32 px): 1 px frame, 6 px cells, 2 px between them.
        float px = 1f / (perSide * 8);
        var cells = new Cell[perSide * perSide];
        for (int row = 0; row < perSide; row++) {
            for (int col = 0; col < perSide; col++) {
                float u = (1 + 8 * col) * px;
                float v = (1 + 8 * row) * px;
                cells[row * perSide + col] = new Cell(u, v + px, u + 6 * px, v + 6 * px, u + px, v, u + 5 * px, v + px, 4);
            }
        }
        return cells;
    }

    /**
     * The slot under {@code hit} (a point on the block at {@code pos}), or -1 when the point isn't on the front.
     */
    public static int slotAt(Direction facing, int perSide, BlockPos pos, Vec3 hit, Direction face) {
        if (face != facing) return -1;
        if (perSide == 1) return 0;
        var local = hit.subtract(pos.getX(), pos.getY(), pos.getZ());
        var right = facing.getCounterClockWise(); // the viewer's right, looking at the front
        double along = right.getAxis().choose(local.x, local.y, local.z);
        double u = right.getAxisDirection() == Direction.AxisDirection.POSITIVE ? along : 1 - along;
        double v = 1 - local.y;
        int col = Math.min(perSide - 1, Math.max(0, (int) (u * perSide)));
        int row = Math.min(perSide - 1, Math.max(0, (int) (v * perSide)));
        return row * perSide + col;
    }
}
