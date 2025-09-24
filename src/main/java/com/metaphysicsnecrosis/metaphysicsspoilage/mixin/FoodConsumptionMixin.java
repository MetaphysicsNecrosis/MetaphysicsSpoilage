package com.metaphysicsnecrosis.metaphysicsspoilage.mixin;

import com.metaphysicsnecrosis.metaphysicsspoilage.Config;
import com.metaphysicsnecrosis.metaphysicsspoilage.manager.TimedFoodManager;
import com.metaphysicsnecrosis.metaphysicsspoilage.spoilage.SpoilageUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin для перехвата потребления еды и проверки её на порчу.
 * Блокирует употребление испорченной еды согласно настройкам мода.
 *
 * @author MetaphysicsNecrosis
 * @version 1.0
 * @since 1.21.8
 */
@Mixin(ItemStack.class)
public class FoodConsumptionMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger(FoodConsumptionMixin.class);

    /**
     * Перехватывает завершение использования предмета (употребление еды).
     * Проверяет еду на порчу и блокирует употребление испорченной еды.
     */
    @Inject(
        method = "finishUsingItem",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onFinishUsingItem(Level level, LivingEntity livingEntity, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack stack = (ItemStack) (Object) this;

        // Работаем только на серверной стороне
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel)) {
            return;
        }

        // Проверяем только еду
        if (!stack.has(DataComponents.FOOD)) {
            return;
        }

        // Проверяем, включена ли система порчи
        try {
            if (!Config.ENABLE_SPOILAGE_SYSTEM.get()) {
                return;
            }
        } catch (Exception e) {
            // Если конфиг не загружен, пропускаем проверку
            LOGGER.debug("Конфиг не загружен при проверке порчи еды: {}", e.getMessage());
            return;
        }

        // Проверяем, имеет ли еда временную метку
        if (!SpoilageUtils.hasTimestamp(stack)) {
            // Еда без временной метки - проверяем настройки
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();

            try {
                if (Config.isAlwaysEdible(itemId)) {
                    // Предмет в белом списке - можно есть
                    return;
                }

                // Блокируем еду без временной метки согласно настройкам
                Config.FoodBlockingMode blockingMode = Config.FOOD_BLOCKING_MODE.get();

                if (blockingMode == Config.FoodBlockingMode.FULL_BLOCK) {
                    // Полная блокировка - отменяем употребление
                    LOGGER.debug("Заблокировано употребление еды без временной метки: {}", itemId);
                    cir.setReturnValue(stack); // Возвращаем тот же предмет (не употребляем)
                    return;
                }
                // Для режима ZERO_NUTRITION обработка происходит в другом месте

            } catch (Exception e) {
                LOGGER.debug("Ошибка при проверке настроек блокировки для {}: {}", itemId, e.getMessage());
                return;
            }
        } else {
            // Еда с временной меткой - проверяем порчу
            if (SpoilageUtils.isItemSpoiled(stack, serverLevel)) {
                String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();

                // ВСЕГДА сначала пытаемся превратить в испорченный вариант
                ItemStack transformedStack = TimedFoodManager.checkAndProcessSpoilage(stack, serverLevel);

                if (!transformedStack.isEmpty() && !transformedStack.equals(stack)) {
                    // Превращение удалось - заменяем на испорченный вариант
                    LOGGER.debug("Еда {} превращена в {} при попытке употребления",
                        itemId, BuiltInRegistries.ITEM.getKey(transformedStack.getItem()));
                    cir.setReturnValue(transformedStack);
                    return;
                } else {
                    // Превращение не удалось - проверяем режим порчи
                    try {
                        Config.SpoilageMode spoilageMode = Config.SPOILAGE_MODE.get();

                        if (spoilageMode == Config.SpoilageMode.INSTANT_DISAPPEAR) {
                            // Только если превращение невозможно - исчезает
                            LOGGER.debug("Испорченная еда {} исчезла при попытке употребления (превращение невозможно)", itemId);
                            cir.setReturnValue(ItemStack.EMPTY);
                            return;
                        } else {
                            // В режиме TRANSFORM_TO_SPOILED блокируем употребление если превращение не удалось
                            LOGGER.debug("Заблокировано употребление испорченной еды {} (превращение не удалось)", itemId);
                            cir.setReturnValue(stack);
                            return;
                        }

                    } catch (Exception e) {
                        LOGGER.debug("Ошибка при получении режима порчи для {}: {}", itemId, e.getMessage());
                        // При ошибке блокируем употребление
                        cir.setReturnValue(stack);
                        return;
                    }
                }
            }
        }

        // Если все проверки прошли успешно, позволяем нормальное употребление
    }
}