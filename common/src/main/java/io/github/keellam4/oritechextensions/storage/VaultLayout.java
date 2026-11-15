package io.github.keellam4.oritechextensions.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * The shapes of the vaults' fronts: where each slot's window sits, in block units seen from the front (u to the
 * right, v down, 0 to 1), and which window a click lands in. Matches the textures from
 * tools/storage/cargo_vault_textures.py.
 */
public enum VaultLayout {
    /** Cargo Vault: one big window. */
    SINGLE(1),
    /** Dual Vault: two wide windows, one above the other. */
    DUAL(2),
    /** Quad Vault: 2 × 2 windows. */
    QUAD(4),
    /** Grid Vault: 4 × 4 windows (a 32 px texture). */
    GRID(16),
    /** Compacting Vault: a wide window on top for the biggest form, two below for the smaller ones. */
    COMPACTING(3),
    /** Dual Compacting Vault: two forms, the bigger one on top (same windows as the Dual Vault). */
    COMPACTING_DUAL(2);

    /** One slot's window: the glass the item floats in and the strip that shows how full it is. */
    public record Cell(float glassU0, float glassV0, float glassU1, float glassV1,
                       float gaugeU0, float gaugeV0, float gaugeU1, float gaugeV1, int gaugePixels) {

        static Cell px(float size, int glassX0, int glassY0, int glassX1, int glassY1, int gaugeX0, int gaugeY, int gaugeX1) {
            return new Cell(glassX0 / size, glassY0 / size, glassX1 / size, glassY1 / size,
                    gaugeX0 / size, gaugeY / size, gaugeX1 / size, (gaugeY + 1) / size, gaugeX1 - gaugeX0);
        }
    }

    private final int slots;
    private final Cell[] cells;

    VaultLayout(int slots) {
        this.slots = slots;
        this.cells = makeCells(slots);
    }

    /** Whether this vault stores one item in several forms. */
    public boolean compacting() {
        return this == COMPACTING || this == COMPACTING_DUAL;
    }

    public int slots() {
        return slots;
    }

    /** Cells in slot order. */
    public Cell[] cells() {
        return cells;
    }

    private static Cell[] makeCells(int slots) {
        return switch (slots) {
            case 1 -> new Cell[]{Cell.px(16, 3, 4, 13, 12, 3, 3, 13)};
            // Wide windows: glass 14 px, gauge 10 px above it.
            case 2 -> new Cell[]{Cell.px(16, 1, 2, 15, 7, 3, 1, 13), Cell.px(16, 1, 10, 15, 15, 3, 9, 13)};
            case 3 -> new Cell[]{Cell.px(16, 1, 2, 15, 7, 3, 1, 13), Cell.px(16, 1, 10, 7, 15, 2, 9, 6), Cell.px(16, 9, 10, 15, 15, 10, 9, 14)};
            default -> grid(slots == 4 ? 2 : 4);
        };
    }

    /** Quad (16 px) and Grid (32 px): 1 px frame, 6 px cells, 2 px between them. */
    private static Cell[] grid(int perSide) {
        float size = perSide * 8;
        var cells = new Cell[perSide * perSide];
        for (int row = 0; row < perSide; row++) {
            for (int col = 0; col < perSide; col++) {
                int x = 1 + 8 * col;
                int y = 1 + 8 * row;
                cells[row * perSide + col] = Cell.px(size, x, y + 1, x + 6, y + 6, x + 1, y, x + 5);
            }
        }
        return cells;
    }

    /** The window at (u, v) on the front. */
    public int slotAt(double u, double v) {
        return switch (this) {
            case SINGLE -> 0;
            case DUAL, COMPACTING_DUAL -> v < 0.5 ? 0 : 1;
            case COMPACTING -> v < 0.5 ? 0 : u < 0.5 ? 1 : 2;
            case QUAD, GRID -> {
                int perSide = this == QUAD ? 2 : 4;
                int col = Math.min(perSide - 1, Math.max(0, (int) (u * perSide)));
                int row = Math.min(perSide - 1, Math.max(0, (int) (v * perSide)));
                yield row * perSide + col;
            }
        };
    }

    /** The window under {@code hit} (a point on the block at {@code pos}), or -1 when the point isn't on the front. */
    public int slotAt(Direction facing, BlockPos pos, Vec3 hit, Direction face) {
        if (face != facing) return -1;
        var local = hit.subtract(pos.getX(), pos.getY(), pos.getZ());
        var right = facing.getCounterClockWise(); // the viewer's right, looking at the front
        double along = right.getAxis().choose(local.x, local.y, local.z);
        double u = right.getAxisDirection() == Direction.AxisDirection.POSITIVE ? along : 1 - along;
        return slotAt(u, 1 - local.y);
    }
}
