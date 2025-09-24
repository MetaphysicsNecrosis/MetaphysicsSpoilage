package com.metaphysicsnecrosis.metaphysicsspoilage.mixin;

import com.metaphysicsnecrosis.metaphysicsspoilage.component.SpoilageHooks;
import com.metaphysicsnecrosis.metaphysicsspoilage.spoilage.SpoilageUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin для обработки добавления предметов в инвентарь игрока.
 * Перехватывает все способы добавления предметов и устанавливает свежие временные метки для еды.
 * Это покрывает команды, торговлю, плагины и другие источники предметов.
 *
 * @author MetaphysicsNecrosis
 * @version 1.0
 * @since 1.21.8
 */
@Mixin(Inventory.class)
public class PlayerInventoryMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerInventoryMixin.class);

    @Shadow @Final public Player player;

    /**
     * Перехватывает добавление предметов в инвентарь и устанавливает свежие временные метки для еды.
     * Это основная точка перехвата, которая покрывает большинство случаев получения предметов.
     */
    @Inject(
        method = "add(Lnet/minecraft/world/item/ItemStack;)Z",
        at = @At("HEAD")
    )
    private void onAddItemToInventory(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack == null || stack.isEmpty()) {
            return;
        }

        // Работаем только на серверной стороне
        Level level = player.level();
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel)) {
            return;
        }

        try {
            // Проверяем, является ли предмет едой
            if (stack.has(DataComponents.FOOD)) {
                // Проверяем, может ли предмет портиться
                if (SpoilageUtils.canItemSpoil(stack.getItem())) {
                    // Проверяем, нет ли уже временной метки (чтобы не перезаписывать)
                    if (!SpoilageUtils.hasTimestamp(stack)) {
                        // Устанавливаем свежую временную метку
                        SpoilageHooks.setFreshTimestamp(stack);

                        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                        LOGGER.debug("Установлена свежая временная метка для еды {} при добавлении в инвентарь игрока {}",
                            itemId, player.getName().getString());
                    }
                }
            }
        } catch (Exception e) {
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            LOGGER.error("Ошибка при обработке добавления предмета {} в инвентарь игрока {}: {}",
                itemId, player.getName().getString(), e.getMessage());
        }
    }

    /**
     * Перехватывает добавление предметов в определенный слот.
     * Дополнительная защита для случаев прямого добавления в слот.
     */
    @Inject(
        method = "add(ILnet/minecraft/world/item/ItemStack;)Z",
        at = @At("HEAD")
    )
    private void onAddItemToSlot(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack == null || stack.isEmpty()) {
            return;
        }

        // Работаем только на серверной стороне
        Level level = player.level();
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel)) {
            return;
        }

        try {
            // Проверяем, является ли предмет едой
            if (stack.has(DataComponents.FOOD)) {
                // Проверяем, может ли предмет портиться
                if (SpoilageUtils.canItemSpoil(stack.getItem())) {
                    // Проверяем, нет ли уже временной метки (чтобы не перезаписывать)
                    if (!SpoilageUtils.hasTimestamp(stack)) {
                        // Устанавливаем свежую временную метку
                        SpoilageHooks.setFreshTimestamp(stack);

                        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                        LOGGER.debug("Установлена свежая временная метка для еды {} при добавлении в слот {} инвентаря игрока {}",
                            itemId, slot, player.getName().getString());
                    }
                }
            }
        } catch (Exception e) {
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            LOGGER.error("Ошибка при обработке добавления предмета {} в слот {} инвентаря игрока {}: {}",
                itemId, slot, player.getName().getString(), e.getMessage());
        }
    }
}