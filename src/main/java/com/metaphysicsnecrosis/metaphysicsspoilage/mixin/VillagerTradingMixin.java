package com.metaphysicsnecrosis.metaphysicsspoilage.mixin;

import com.metaphysicsnecrosis.metaphysicsspoilage.component.SpoilageHooks;
import com.metaphysicsnecrosis.metaphysicsspoilage.spoilage.SpoilageUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin для обработки торговли с жителями.
 * Устанавливает свежие временные метки для еды, получаемой в результате торговли.
 *
 * @author MetaphysicsNecrosis
 * @version 1.0
 * @since 1.21.8
 */
@Mixin(MerchantOffer.class)
public class VillagerTradingMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger(VillagerTradingMixin.class);

    /**
     * Перехватывает результат торговли и устанавливает свежие временные метки для еды.
     * Оптимизированная версия - обновляет метки только если они устарели.
     */
    @Inject(
        method = "getResult()Lnet/minecraft/world/item/ItemStack;",
        at = @At("RETURN")
    )
    private void onGetTradeResult(CallbackInfoReturnable<ItemStack> cir) {
        ItemStack result = cir.getReturnValue();

        if (result == null || result.isEmpty()) {
            return;
        }

        try {
            // Проверяем, является ли предмет едой
            if (result.has(DataComponents.FOOD)) {
                // Проверяем, может ли предмет портиться
                if (SpoilageUtils.canItemSpoil(result.getItem())) {

                    // Получаем текущий день
                    long currentDay = getCurrentDay();

                    // Проверяем, нужно ли обновлять метку
                    if (shouldUpdateTimestamp(result, currentDay)) {
                        // Устанавливаем свежую временную метку
                        SpoilageHooks.setFreshTimestamp(result);

                        String itemId = BuiltInRegistries.ITEM.getKey(result.getItem()).toString();
                        LOGGER.debug("Обновлена временная метка для еды {} из торговли (день: {})", itemId, currentDay);
                    }
                }
            }
        } catch (Exception e) {
            String itemId = BuiltInRegistries.ITEM.getKey(result.getItem()).toString();
            LOGGER.error("Ошибка при обработке торговли для предмета {}: {}", itemId, e.getMessage());
        }
    }

    /**
     * Проверяет, нужно ли обновлять временную метку предмета
     */
    private boolean shouldUpdateTimestamp(ItemStack stack, long currentDay) {
        // Если нет метки - обязательно ставим
        if (!SpoilageUtils.hasTimestamp(stack)) {
            return true;
        }

        // Получаем существующую метку
        long existingDay = SpoilageUtils.getCreationDay(stack);

        // Если метка старше текущего дня - обновляем
        return existingDay < currentDay;
    }

    /**
     * Получает текущий игровой день (копия из SpoilageHooks)
     */
    private long getCurrentDay() {
        try {
            var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                var overworld = server.overworld();
                if (overworld != null) {
                    return com.metaphysicsnecrosis.metaphysicsspoilage.time.WorldDayTracker.getInstance(overworld).getCurrentDay();
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Не удалось получить серверный уровень: {}", e.getMessage());
        }
        return 0L;
    }

}