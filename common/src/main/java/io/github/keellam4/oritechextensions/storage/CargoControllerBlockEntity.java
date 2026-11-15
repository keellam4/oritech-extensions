package io.github.keellam4.oritechextensions.storage;

import io.github.keellam4.oritechextensions.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** The Cargo Controller: finds its network of touching vaults (refreshed once a second, or sooner when it breaks). */
public class CargoControllerBlockEntity extends BlockEntity {

    private CargoNetwork network;
    private long discoveredAt = Long.MIN_VALUE;

    public CargoControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.CARGO_CONTROLLER_ENTITY.get(), pos, state);
    }

    public CargoNetwork network() {
        long now = level == null ? 0 : level.getGameTime();
        if (network == null || now - discoveredAt >= 20 || now < discoveredAt || !network.isValid()) {
            network = level == null ? CargoNetwork.EMPTY : CargoNetwork.discover(level, worldPosition);
            discoveredAt = now;
        }
        return network;
    }

    /** Forget the network so the next use looks again (a vault next to it was placed or broken). */
    public void invalidate() {
        network = null;
    }
}
