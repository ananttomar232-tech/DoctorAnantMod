package com.dranant.block;

import com.dranant.clinic.ClinicBuilder;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Tier 1-10 clinic summoner. Placing it (or right-clicking it) clears the volume and builds clinic_level_N
 * from data/dranant/structure/clinic_level_N.nbt, falling back to a procedural clinic if that file is missing or empty.
 */
public class ClinicSummonerBlock extends Block {
    public static final MapCodec<ClinicSummonerBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.fieldOf("tier").forGetter(ClinicSummonerBlock::getTier),
            propertiesCodec()
    ).apply(instance, ClinicSummonerBlock::new));

    private final int tier;

    public ClinicSummonerBlock(int tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public int getTier() {
        return tier;
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel serverLevel) {
            ClinicBuilder.summonClinic(serverLevel, pos, tier, placer instanceof Player p ? p : null);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel serverLevel) {
            ClinicBuilder.summonClinic(serverLevel, pos, tier, player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Clinic Tier " + tier).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("Place to instantly build the clinic + 2 assistants").withStyle(ChatFormatting.GRAY));
    }
}
