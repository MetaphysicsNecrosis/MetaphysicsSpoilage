package com.metaphysicsnecrosis.metaphysicsspoilage.spoilage;

import com.metaphysicsnecrosis.metaphysicsspoilage.MetaphysicsSpoilage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Обработчик событий для автоматической установки временных меток на предметы
 */
@EventBusSubscriber(modid = MetaphysicsSpoilage.MODID)
public class SpoilageEventHandler {

    /**
     * Устанавливает временную метку при создании предмета в мире
     */
    @SubscribeEvent
    public static void onItemEntityJoinWorld(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }

        if (event.getEntity() instanceof ItemEntity itemEntity) {
            ItemStack itemStack = itemEntity.getItem();
            if (!itemStack.isEmpty() && event.getLevel() instanceof ServerLevel serverLevel) {
                // Автоматически устанавливаем временную метку для портящихся предметов
                SpoilageUtils.autoSetTimestamp(itemStack, serverLevel);
            }
        }
    }

    /**
     * Устанавливает временную метку при получении предмета игроком через крафт
     */
    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }

        ItemStack craftedItem = event.getCrafting();
        if (!craftedItem.isEmpty() && event.getEntity().level() instanceof ServerLevel serverLevel) {
            // Устанавливаем временную метку на скрафченный предмет
            SpoilageUtils.autoSetTimestamp(craftedItem, serverLevel);
        }
    }

    /**
     * Устанавливает временную метку при получении предмета игроком через плавку
     */
    @SubscribeEvent
    public static void onItemSmelted(PlayerEvent.ItemSmeltedEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }

        ItemStack smeltedItem = event.getSmelting();
        if (!smeltedItem.isEmpty() && event.getEntity().level() instanceof ServerLevel serverLevel) {
            // Устанавливаем временную метку на переплавленный предмет
            SpoilageUtils.autoSetTimestamp(smeltedItem, serverLevel);
        }
    }
}