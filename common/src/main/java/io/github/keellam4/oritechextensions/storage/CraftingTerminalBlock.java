package io.github.keellam4.oritechextensions.storage;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

/**
 * The Crafting Terminal: placed against any vault of a Cargo Wall, it reaches the same network as a Cargo
 * Controller and opens a terminal with a 3 × 3 crafting grid that pulls its ingredients from the vaults.
 */
public class CraftingTerminalBlock extends CargoControllerBlock {

    public static final MapCodec<CraftingTerminalBlock> CODEC = simpleCodec(CraftingTerminalBlock::new);

    public CraftingTerminalBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }
}
