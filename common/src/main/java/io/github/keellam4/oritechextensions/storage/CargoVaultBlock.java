package io.github.keellam4.oritechextensions.storage;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The vaults: bulk storage in one or more windows (see {@link VaultLayout}).
 * Each slot's item floats in its glass window, the strip above it shows how full it is. Interactions are in
 * {@link CargoVault}.
 */
public class CargoVaultBlock extends HorizontalDirectionalBlock implements EntityBlock {

    /** Set while a Redstone upgrade is installed: the vault then gives off a signal as strong as it is full. */
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty REDSTONE =
            net.minecraft.world.level.block.state.properties.BooleanProperty.create("redstone");

    private final VaultLayout layout;
    private final MapCodec<CargoVaultBlock> codec;

    public CargoVaultBlock(Properties properties, VaultLayout layout) {
        super(properties);
        this.layout = layout;
        this.codec = simpleCodec(p -> new CargoVaultBlock(p, layout));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(REDSTONE, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return codec;
    }

    public VaultLayout layout() {
        return layout;
    }

    public int slots() {
        return layout.slots();
    }

    /** The slot under {@code hit}, or -1 when it isn't on the front. */
    public int slotAt(BlockState state, BlockPos pos, BlockHitResult hit) {
        return layout.slotAt(state.getValue(FACING), pos, hit.getLocation(), hit.getDirection());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, REDSTONE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return layout.compacting() ? new CompactingVaultBlockEntity(pos, state) : new CargoVaultBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof CargoVaultBlockEntity vault)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (slotAt(state, pos, hit) < 0) {
            // Side or top: the upgrade screen.
            if (!level.isClientSide()) openUpgrades(vault, player);
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        // An item the vault can't take: let it be used normally (placing a block against the vault, say).
        if (!vault.accepts(stack)) return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        if (!level.isClientSide()) CargoVault.insertHeld(vault, player, hand, slotAt(state, pos, hit));
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof CargoVaultBlockEntity vault)) return InteractionResult.PASS;
        if (!level.isClientSide()) {
            if (player.isShiftKeyDown()) {
                CargoVault.toggleLock(vault, player);
            } else if (slotAt(state, pos, hit) < 0) {
                openUpgrades(vault, player);
            } else {
                CargoVault.insertAll(level, vault, player);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    private static void openUpgrades(CargoVaultBlockEntity vault, Player player) {
        player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                (id, inventory, p) -> new VaultUpgradeMenu(id, inventory, vault),
                vault.getBlockState().getBlock().getName()));
    }

    @Nullable
    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return (tickLevel, tickPos, tickState, entity) -> {
            if (entity instanceof CargoVaultBlockEntity vault) CargoVaultBlockEntity.tick(tickLevel, tickPos, tickState, vault);
        };
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return state.getValue(REDSTONE);
    }

    @Override
    protected int getSignal(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, Direction direction) {
        if (!state.getValue(REDSTONE) || !(level.getBlockEntity(pos) instanceof CargoVaultBlockEntity vault)) return 0;
        return vault.signal();
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof CargoVaultBlockEntity vault ? vault.signal() : 0;
    }
}
