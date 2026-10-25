package com.blackparasite.item;

import com.blackparasite.logic.ParasiteService;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Échantillon de parasite. Un clic droit infecte le joueur (une seule fois) : c'est la « première infection ».
 */
public class ParasiteSampleItem extends Item {

    public ParasiteSampleItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer sp)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (ParasiteService.isInfected(sp)) {
            sp.displayClientMessage(Component.literal("Le parasite est déjà en vous."), true);
            return InteractionResultHolder.fail(stack);
        }
        if (ParasiteService.infect(sp)) {
            if (!sp.getAbilities().instabuild) stack.shrink(1);
            return InteractionResultHolder.consume(stack);
        }
        return InteractionResultHolder.fail(stack);
    }
}
