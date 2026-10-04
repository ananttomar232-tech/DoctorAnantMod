package com.dranant.block;

import com.dranant.block.entity.DiamondCashRegisterBlockEntity;
import com.dranant.clinic.ClinicQueueManager;
import com.dranant.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Containers;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The clinic's heart: collects every diamond the Assistant Pharmacist earns and anchors both villager queues.
 * FACING points toward the customers (Line 1 forms in front of it). Right-click to withdraw the stored diamonds.
 */
public class DiamondCashRegisterBlock extends BaseEntityBlock {
    public static final MapCodec<DiamondCashRegisterBlock> CODEC = simpleCodec(DiamondCashRegisterBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final VoxelShape SHAPE = Block.box(1, 0, 2, 15, 15, 14);

    public DiamondCashRegisterBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DiamondCashRegisterBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.DIAMOND_CASH_REGISTER.get(), DiamondCashRegisterBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof DiamondCashRegisterBlockEntity register)) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (player.isShiftKeyDown()) {
            player.displayClientMessage(Component.literal("Register: " + register.getStoredDiamonds() + " Diamonds | Line 1: "
                    + register.getLine1().size() + " | Line 2: " + register.getLine2().size()
                    + (register.getBedHead() != null ? " | Lab bed linked" : " | No lab bed nearby")).withStyle(ChatFormatting.AQUA), false);
            return InteractionResult.SUCCESS;
        }
        int stored = register.takeAllDiamonds();
        if (stored <= 0) {
            player.displayClientMessage(Component.literal("The cash register is empty.").withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.SUCCESS;
        }
        int remaining = stored;
        while (remaining > 0) {
            int n = Math.min(64, remaining);
            ItemStack stack = new ItemStack(Items.DIAMOND, n);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            remaining -= n;
        }
        level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 1.0F, 1.2F);
        player.displayClientMessage(Component.literal("Collected " + stored + " Diamonds from the register!").withStyle(ChatFormatting.AQUA), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            if (level.getBlockEntity(pos) instanceof DiamondCashRegisterBlockEntity register) {
                int stored = register.takeAllDiamonds();
                while (stored > 0) {
                    int n = Math.min(64, stored);
                    Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, new ItemStack(Items.DIAMOND, n));
                    stored -= n;
                }
            }
            ClinicQueueManager.releaseAll(serverLevel, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
