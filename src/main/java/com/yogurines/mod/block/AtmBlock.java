package com.yogurines.mod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Cajero automático de 2 bloques de alto. La mitad de abajo dibuja el modelo entero;
 * la de arriba es invisible y solo sirve para ocupar el hueco y tener colisión.
 */
public class AtmBlock extends HorizontalDirectionalBlock {
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    private static final VoxelShape LOWER = Block.box(0, 0, 0, 16, 16, 16);
    private static final VoxelShape UPPER = Block.box(0, 0, 0, 16, 16, 16);

    public AtmBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() < level.getMaxBuildHeight() - 1 && level.getBlockState(pos.above()).canBeReplaced(context)) {
            return defaultBlockState()
                    .setValue(FACING, context.getHorizontalDirection().getOpposite())
                    .setValue(HALF, DoubleBlockHalf.LOWER);
        }
        return null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
        if (!level.isClientSide) {
            // Toque metálico suave encima del sonido normal de colocar: yunque muy bajito y agudo.
            level.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.12F, 1.75F);
        }
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        if (direction.getAxis() == Direction.Axis.Y && (half == DoubleBlockHalf.LOWER) == (direction == Direction.UP)) {
            return neighbor.is(this) && neighbor.getValue(HALF) != half
                    ? state.setValue(FACING, neighbor.getValue(FACING))
                    : Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER) {
            return true;
        }
        BlockState below = level.getBlockState(pos.below());
        return below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER;
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // En creativo, al romper la parte de arriba quitamos la de abajo sin que suelte nada
        // (si no, al quedarse sin la otra mitad se rompería "sola" y soltaría el cajero).
        // Lo mismo si se rompe sin pico. En supervivencia solo suelta la mitad de abajo (ver la loot table), así nunca sale doble.
        if (!level.isClientSide && state.getValue(HALF) == DoubleBlockHalf.UPPER
                && (player.isCreative() || !player.hasCorrectToolForDrops(state))) {
            BlockPos below = pos.below();
            BlockState belowState = level.getBlockState(below);
            if (belowState.is(this) && belowState.getValue(HALF) == DoubleBlockHalf.LOWER) {
                level.setBlock(below, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                level.levelEvent(player, 2001, below, Block.getId(belowState));
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? UPPER : LOWER;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            player.displayClientMessage(Component.translatable("block.yogurines.atm.soon"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
