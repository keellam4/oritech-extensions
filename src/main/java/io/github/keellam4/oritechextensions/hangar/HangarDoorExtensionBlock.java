package io.github.keellam4.oritechextensions.hangar;

import com.mojang.serialization.MapCodec;
import io.github.keellam4.oritechextensions.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.world.item.component.TooltipProvider;
import java.util.function.Consumer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import rearth.oritech.block.blocks.decorative.HangarDoorBlock;
import rearth.oritech.block.blocks.decorative.HangarDoorHelperBlock;
import rearth.oritech.init.BlockContent;

import java.util.ArrayList;

import static rearth.oritech.block.blocks.decorative.HangarDoorBlock.OPENED;
import static rearth.oritech.block.blocks.decorative.HangarDoorBlock.ROTATED;
import static rearth.oritech.block.blocks.decorative.HangarDoorBlock.SURFACE;

/**
 * One extra block of length for an Oritech Industrial Hangar Door.
 * <p>
 * An Oritech hangar door is an anchor block plus two helper blocks (part 1 and 2) laid out along the
 * door's segment direction. Extensions continue that line: extension 1 sits right after helper part 2,
 * extension 2 after extension 1, and so on. Each extension copies the surface/rotation of the door and
 * mirrors its open state, which it reads from the block directly behind it ("parent"). Because Oritech
 * updates its helpers with regular block updates, every state change ripples down the chain without
 * any changes to Oritech itself.
 */
public class HangarDoorExtensionBlock extends Block implements EntityBlock, TooltipProvider {

    public static final MapCodec<HangarDoorExtensionBlock> CODEC = simpleCodec(HangarDoorExtensionBlock::new);

    /** Position in the chain: 1 = directly after Oritech's helper part 2. */
    public static final IntegerProperty INDEX = IntegerProperty.create("index", 1, Config.ABSOLUTE_MAX_DOOR_LENGTH - Config.BASE_DOOR_LENGTH);

    public HangarDoorExtensionBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(SURFACE, Direction.NORTH)
                .setValue(OPENED, false)
                .setValue(ROTATED, false)
                .setValue(INDEX, 1));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SURFACE, OPENED, ROTATED, INDEX);
    }

    // ---------------------------------------------------------------- geometry helpers

    public static Direction segmentDirection(BlockState state) {
        return HangarDoorBlock.getSegmentDirection(state.getValue(SURFACE), state.getValue(ROTATED));
    }

    /** The block this extension hangs off: helper part 2 for index 1, otherwise the previous extension. */
    public static BlockPos parentPos(BlockPos pos, BlockState state) {
        return pos.relative(segmentDirection(state).getOpposite());
    }

    /** Oritech's anchor block: 2 helpers + (index) extensions behind this block. */
    public static BlockPos anchorPos(BlockPos pos, BlockState state) {
        return pos.relative(segmentDirection(state), -(state.getValue(INDEX) + 2));
    }

    private boolean isValidParent(BlockState self, BlockState parent) {
        var surface = self.getValue(SURFACE);
        var rotated = self.getValue(ROTATED);
        int index = self.getValue(INDEX);

        if (index == 1) {
            return parent.is(BlockContent.HANGAR_DOOR_HELPER)
                    && parent.getValue(HangarDoorHelperBlock.PART) == 2
                    && parent.getValue(SURFACE) == surface
                    && parent.getValue(ROTATED) == rotated;
        }

        return parent.is(this)
                && parent.getValue(INDEX) == index - 1
                && parent.getValue(SURFACE) == surface
                && parent.getValue(ROTATED) == rotated;
    }

    // ---------------------------------------------------------------- placement

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        var level = ctx.getLevel();
        var pos = ctx.getClickedPos();

        // Look at the block the player clicked first, then every other neighbour.
        var candidates = new ArrayList<BlockPos>();
        candidates.add(pos.relative(ctx.getClickedFace().getOpposite()));
        for (var dir : Direction.values()) {
            var neighbor = pos.relative(dir);
            if (!candidates.contains(neighbor)) candidates.add(neighbor);
        }

        boolean hitLengthLimit = false;
        for (var candidatePos : candidates) {
            var candidate = level.getBlockState(candidatePos);
            int index;

            if (candidate.is(BlockContent.HANGAR_DOOR_HELPER) && candidate.getValue(HangarDoorHelperBlock.PART) == 2) {
                index = 1;
            } else if (candidate.is(this)) {
                index = candidate.getValue(INDEX) + 1;
            } else {
                continue;
            }

            var surface = candidate.getValue(SURFACE);
            var rotated = candidate.getValue(ROTATED);
            // The new block has to continue the door in a straight line, not sit beside it.
            if (!candidatePos.relative(HangarDoorBlock.getSegmentDirection(surface, rotated)).equals(pos)) continue;

            if (index > Config.maxExtensions() || index > INDEX.getPossibleValues().size()) {
                hitLengthLimit = true;
                continue;
            }

            return defaultBlockState()
                    .setValue(SURFACE, surface)
                    .setValue(ROTATED, rotated)
                    .setValue(OPENED, candidate.getValue(OPENED))
                    .setValue(INDEX, index);
        }

        var player = ctx.getPlayer();
        if (player != null && level.isClientSide()) {
            player.sendOverlayMessage(hitLengthLimit
                    ? Component.translatable("message.oritech_extensions.hangar_door_extension.too_long", Config.maxTotalLength())
                    : Component.translatable("message.oritech_extensions.hangar_door_extension.no_door"));
        }
        return null;
    }

    // ---------------------------------------------------------------- staying in sync with the door

    /**
     * Called whenever a neighbour changes. When the parent disappears the extension breaks (and drops),
     * which cascades down the rest of the chain. When the parent opens or closes we copy its state.
     */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        if (direction == segmentDirection(state).getOpposite()) {
            if (!isValidParent(state, neighborState)) return Blocks.AIR.defaultBlockState();

            boolean parentOpen = neighborState.getValue(OPENED);
            if (parentOpen != state.getValue(OPENED)) return state.setValue(OPENED, parentOpen);
        }
        return state;
    }

    /** Safety net in case a state change arrives as a plain neighbour update instead of a shape update. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, @Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, orientation, movedByPiston);
        if (level.isClientSide()) return;

        var parentState = level.getBlockState(parentPos(pos, state));
        if (!isValidParent(state, parentState)) {
            level.destroyBlock(pos, true);
            return;
        }

        boolean parentOpen = parentState.getValue(OPENED);
        if (parentOpen != state.getValue(OPENED)) {
            level.setBlockAndUpdate(pos, state.setValue(OPENED, parentOpen));
        }
    }

    // ---------------------------------------------------------------- shapes & rendering

    private static VoxelShape closedShape(BlockState state) {
        var shape = HangarDoorBlock.getClosedShape(state.getValue(SURFACE), state.getValue(ROTATED));
        return shape != null ? shape : Shapes.block();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return closedShape(state);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(OPENED) ? Shapes.empty() : closedShape(state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        // drawn by HangarDoorExtensionRenderer
        return RenderShape.INVISIBLE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HangarDoorExtensionBlockEntity(pos, state);
    }

    @Override
    public void addToTooltip(Item.TooltipContext context, Consumer<Component> tooltip, TooltipFlag flag, DataComponentGetter components) {
        tooltip.accept(Component.translatable("tooltip.oritech_extensions.hangar_door_extension.1").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("tooltip.oritech_extensions.hangar_door_extension.2", Config.maxTotalLength()).withStyle(ChatFormatting.GRAY));
    }
}
