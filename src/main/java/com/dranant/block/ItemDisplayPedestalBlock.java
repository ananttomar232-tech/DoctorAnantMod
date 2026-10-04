package com.dranant.block;

import com.dranant.block.entity.DiamondCashRegisterBlockEntity;
import com.dranant.block.entity.ItemDisplayPedestalBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * ProBoiz-style shop pedestal. The stored item floats 0.6 blocks above it, spinning 360 degrees, with a price hologram.
 * Right-click = buy (diamonds are deducted). Creative + Shift + Right-click = edit: hold an item to display it,
 * hold diamonds to set the price to the stack size, empty hand to clear.
 */
public class ItemDisplayPedestalBlock extends BaseEntityBlock {
    public static final MapCodec<ItemDisplayPedestalBlock> CODEC = simpleCodec(ItemDisplayPedestalBlock::new);
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(0, 0, 0, 16, 3, 16),
            Block.box(3, 3, 3, 13, 13, 13),
            Block.box(1, 13, 1, 15, 16, 15));

    public ItemDisplayPedestalBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
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
        return new ItemDisplayPedestalBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ItemDisplayPedestalBlockEntity pedestal)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (player.isShiftKeyDown() && player.getAbilities().instabuild) {
            if (!level.isClientSide) {
                if (stack.is(Items.DIAMOND)) {
                    pedestal.setDiamondPrice(stack.getCount());
                    player.displayClientMessage(Component.literal("Price set to " + stack.getCount() + " Diamonds").withStyle(ChatFormatting.GOLD), true);
                } else {
                    pedestal.setDisplayedItem(stack.copyWithCount(1));
                    player.displayClientMessage(Component.literal("Now displaying: ").append(stack.getHoverName()).withStyle(ChatFormatting.AQUA), true);
                }
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide) {
            tryPurchase((ServerLevel) level, pos, player, pedestal);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ItemDisplayPedestalBlockEntity pedestal)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            if (player.isShiftKeyDown() && player.getAbilities().instabuild) {
                pedestal.setDisplayedItem(ItemStack.EMPTY);
                player.displayClientMessage(Component.literal("Pedestal cleared").withStyle(ChatFormatting.GRAY), true);
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);
            } else {
                tryPurchase((ServerLevel) level, pos, player, pedestal);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void tryPurchase(ServerLevel level, BlockPos pos, Player player, ItemDisplayPedestalBlockEntity pedestal) {
        ItemStack display = pedestal.getDisplayedItem();
        if (display.isEmpty()) {
            player.displayClientMessage(Component.literal("This pedestal is empty.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        int price = pedestal.getDiamondPrice();
        boolean free = player.getAbilities().instabuild;
        if (!free) {
            int owned = countDiamonds(player.getInventory());
            if (owned < price) {
                player.displayClientMessage(Component.literal("You need " + price + " Diamonds (you have " + owned + ")").withStyle(ChatFormatting.RED), true);
                level.playSound(null, pos, SoundEvents.VILLAGER_NO, SoundSource.BLOCKS, 1.0F, 1.0F);
                return;
            }
            removeDiamonds(player.getInventory(), price);
        }
        ItemStack bought = display.copyWithCount(1);
        if (!player.getInventory().add(bought)) {
            player.drop(bought, false);
        }
        level.playSound(null, pos, SoundEvents.VILLAGER_CELEBRATE, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1.6, pos.getZ() + 0.5, 25, 0.45, 0.45, 0.45, 0.1);
        player.displayClientMessage(Component.literal("Purchased ").append(display.getHoverName()).append(" for " + price + " Diamonds!").withStyle(ChatFormatting.GREEN), true);
        if (!free && price > 0) {
            DiamondCashRegisterBlockEntity.depositToNearest(level, pos, price);
        }
    }

    public static int countDiamonds(Inventory inventory) {
        int total = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (s.is(Items.DIAMOND)) total += s.getCount();
        }
        return total;
    }

    public static void removeDiamonds(Inventory inventory, int amount) {
        int remaining = amount;
        for (int i = 0; i < inventory.getContainerSize() && remaining > 0; i++) {
            ItemStack s = inventory.getItem(i);
            if (s.is(Items.DIAMOND)) {
                int take = Math.min(remaining, s.getCount());
                s.shrink(take);
                remaining -= take;
            }
        }
        inventory.setChanged();
    }
}
