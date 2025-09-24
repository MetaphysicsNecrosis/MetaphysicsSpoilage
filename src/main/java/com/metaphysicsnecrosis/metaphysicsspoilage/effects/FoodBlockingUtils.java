package com.metaphysicsnecrosis.metaphysicsspoilage.effects;

import com.metaphysicsnecrosis.metaphysicsspoilage.Config;
import com.metaphysicsnecrosis.metaphysicsspoilage.manager.TimedFoodManager;
import com.metaphysicsnecrosis.metaphysicsspoilage.spoilage.SpoilageUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Утилитный класс для работы с системой блокировки еды.
 *
 * Предоставляет методы для:
 * - Проверки необходимости блокировки
 * - Валидации системы блокировки
 * - Интеграции с SpoilageHooks (новая упрощенная система)
 *
 * @author MetaphysicsNecrosis
 * @version 1.0
 * @since 1.21.8
 */
public class FoodBlockingUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(FoodBlockingUtils.class);

    /**
     * Проверяет, нужно ли применять блокировку к данному предмету.
     *
     * @param stack ItemStack для проверки
     * @return true если нужно применить блокировку
     */
    public static boolean shouldApplyBlocking(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        // Проверяем, что система включена
        if (!Config.ENABLE_SPOILAGE_SYSTEM.get()) {
            return false;
        }

        // Проверяем, что предмет является едой
        FoodProperties foodProps = stack.get(DataComponents.FOOD);
        if (foodProps == null) {
            return false;
        }

        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();

        // Проверяем исключения
        if (Config.isAlwaysEdible(itemId)) {
            return false;
        }

        // Проверяем исключения - если предмет не может портиться (canSpoil: false), он должен оставаться съедобным
        if (!SpoilageUtils.canItemSpoil(stack.getItem())) {
            return false;
        }

        // Проверяем, есть ли уже временная метка
        if (TimedFoodManager.isTimedFood(stack)) {
            return false;
        }

        return true;
    }

    /**
     * Применяет логику блокировки к ItemStack (фиктивная операция для совместимости).
     *
     * @param stack ItemStack для обработки
     * @return true если блокировка была "применена"
     */
    public static boolean applyFoodBlockingEffect(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        // В NeoForge 1.21.8 блокировка осуществляется через события,
        // а не через ConsumeEffect компоненты
        if (shouldApplyBlocking(stack)) {
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            LOGGER.debug("Система блокировки активна для предмета: {} (обрабатывается через события)", itemId);
            return true;
        }

        return false;
    }

    /**
     * Массово применяет блокировку к списку ItemStack.
     *
     * @param stacks Список ItemStack для обработки
     * @return Количество обработанных предметов
     */
    public static int applyBlockingToMultiple(List<ItemStack> stacks) {
        if (stacks == null || stacks.isEmpty()) {
            return 0;
        }

        int appliedCount = 0;
        for (ItemStack stack : stacks) {
            if (applyFoodBlockingEffect(stack)) {
                appliedCount++;
            }
        }

        if (appliedCount > 0) {
            LOGGER.debug("Система блокировки активна для {} предметов из {}", appliedCount, stacks.size());
        }

        return appliedCount;
    }

    /**
     * Проверяет совместимость предмета с системой блокировки.
     *
     * @param stack ItemStack для проверки
     * @return true если предмет совместим
     */
    public static boolean isCompatibleItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        // Проверяем, что предмет является едой
        FoodProperties foodProps = stack.get(DataComponents.FOOD);
        if (foodProps == null) {
            return false;
        }

        // Проверяем, что предмет может быть изменен
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (Config.isItemExcluded(itemId)) {
            return false;
        }

        return true;
    }

    /**
     * Получает статистику работы системы блокировки.
     *
     * @return Строка со статистикой
     */
    public static String getBlockingStatistics() {
        return String.format(
            "FoodBlockingUtils - Система включена: %s, Режим блокировки: %s, Исключений: %d",
            Config.ENABLE_SPOILAGE_SYSTEM.get(),
            Config.FOOD_BLOCKING_MODE.get().getName(),
            Config.ALWAYS_EDIBLE_ITEMS.get().size()
        );
    }

    /**
     * Валидирует корректность работы утилит блокировки.
     *
     * @return true если все работает корректно
     */
    public static boolean validateUtils() {
        try {
            // Проверяем доступность Config
            Config.FoodBlockingMode mode = Config.FOOD_BLOCKING_MODE.get();

            // Проверяем доступность TimedFoodManager
            if (!TimedFoodManager.validateManager()) {
                LOGGER.error("TimedFoodManager недоступен для FoodBlockingUtils");
                return false;
            }

            // Проверяем доступность FoodBlockingConsumeEffect
            if (!FoodBlockingConsumeEffect.validateEffect()) {
                LOGGER.error("FoodBlockingConsumeEffect недоступен для FoodBlockingUtils");
                return false;
            }

            LOGGER.info("FoodBlockingUtils валидация прошла успешно");
            return true;

        } catch (Exception e) {
            LOGGER.error("Ошибка валидации FoodBlockingUtils", e);
            return false;
        }
    }

    /**
     * Проверяет, активна ли система блокировки для данного режима.
     *
     * @return true если система активна
     */
    public static boolean isSystemActive() {
        return Config.ENABLE_SPOILAGE_SYSTEM.get() &&
               Config.FOOD_BLOCKING_MODE.get() != null;
    }

    /**
     * Получает информацию о режиме блокировки.
     *
     * @return Описание текущего режима блокировки
     */
    public static String getBlockingModeInfo() {
        if (!Config.ENABLE_SPOILAGE_SYSTEM.get()) {
            return "Система порчи отключена";
        }

        Config.FoodBlockingMode mode = Config.FOOD_BLOCKING_MODE.get();
        return switch (mode) {
            case FULL_BLOCK -> "Полная блокировка еды без временной метки";
            case ZERO_NUTRITION -> "Еда без временной метки не восстанавливает голод";
        };
    }

    /**
     * Проверяет, должен ли предмет быть заблокирован для употребления.
     *
     * @param stack ItemStack для проверки
     * @return true если предмет должен быть заблокирован
     */
    public static boolean shouldBlockConsumption(ItemStack stack) {
        return FoodBlockingConsumeEffect.shouldBlockFood(stack);
    }
}