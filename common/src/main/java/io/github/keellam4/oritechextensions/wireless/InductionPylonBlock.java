package io.github.keellam4.oritechextensions.wireless;

import com.mojang.serialization.MapCodec;
import io.github.keellam4.oritechextensions.ModContent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The Induction Pylon: two blocks tall (it places its top half itself, like a door). Cables connect to the
 * bottom. Right-click shows its charge and who it charges; sneak + right-click (owner) changes who it charges.
 */
public class InductionPylonBlock extends Block implements EntityBlock {

    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    private static final VoxelShape SHAPE = Shapes.box(1 / 16.0, 0, 1 / 16.0, 15 / 16.0, 1, 15 / 16.0);

    private final PylonStyle style;

    public InductionPylonBlock(PylonStyle style, Properties properties) {
        super(properties);
        this.style = style;
        registerDefaultState(stateDefinition.any().setValue(HALF, DoubleBlockHalf.LOWER));
    }

    public PylonStyle style() {
        return style;
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(properties -> new InductionPylonBlock(style, properties));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HALF);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    // ---------------------------------------------------------------- two halves

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        var pos = context.getClickedPos();
        var level = context.getLevel();
        if (pos.getY() >= level.getMaxBuildHeight() - 1 || !level.getBlockState(pos.above()).canBeReplaced(context)) return null;
        return defaultBlockState();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), 3);
        if (placer instanceof Player player && level.getBlockEntity(pos) instanceof InductionPylonBlockEntity pylon) pylon.setOwner(player);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        var half = state.getValue(HALF);
        boolean towardsOther = direction == (half == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN);
        if (towardsOther && !(neighbour.is(this) && neighbour.getValue(HALF) != half)) {
            return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER) return true;
        var below = level.getBlockState(pos.below());
        return below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // Breaking the top in creative shouldn't drop the bottom; breaking the top in survival drops the pylon once.
        if (!level.isClientSide() && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            var below = pos.below();
            var lower = level.getBlockState(below);
            if (lower.is(this) && lower.getValue(HALF) == DoubleBlockHalf.LOWER) {
                if (player.isCreative()) level.setBlock(below, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 35);
                else level.destroyBlock(below, true, player);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    // ---------------------------------------------------------------- block entity

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new InductionPylonBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || state.getValue(HALF) != DoubleBlockHalf.LOWER) return null;
        return (l, pos, s, entity) -> {
            if (entity instanceof InductionPylonBlockEntity pylon) InductionPylonBlockEntity.serverTick(l, pos, s, pylon);
        };
    }

    @Nullable
    public static InductionPylonBlockEntity pylonAt(BlockGetter level, BlockPos pos, BlockState state) {
        var lower = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        return level.getBlockEntity(lower) instanceof InductionPylonBlockEntity pylon ? pylon : null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        var pylon = pylonAt(level, pos, state);
        if (pylon == null) return InteractionResult.PASS;
        if (!level.isClientSide()) {
            if (player.isShiftKeyDown() && pylon.isOwner(player)) pylon.cycleAccess();
            player.displayClientMessage(Component.translatable("message.oritech_extensions.pylon.status",
                    String.format("%,d", pylon.energy()), String.format("%,d", pylon.capacity()),
                    Component.translatable("message.oritech_extensions.pylon.access." + pylon.access().name().toLowerCase(java.util.Locale.ROOT)))
                    .withStyle(ChatFormatting.AQUA), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    /** All three styles share one block entity type. */
    public static Block[] all() {
        return new Block[]{ModContent.INDUCTION_PYLON.get(), ModContent.INDUCTION_PYLON_OBELISK.get(), ModContent.INDUCTION_PYLON_RINGS.get()};
    }
}
