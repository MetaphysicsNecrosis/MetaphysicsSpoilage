package com.metaphysicsnecrosis.metaphysicsspoilage.events;

import com.metaphysicsnecrosis.metaphysicsspoilage.Config;
import com.metaphysicsnecrosis.metaphysicsspoilage.manager.TimedFoodManager;
import com.metaphysicsnecrosis.metaphysicsspoilage.spoilage.SpoilageUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Демонстрационный класс для тестирования системы автозамены еды.
 * Показывает как работает FoodReplacementHandler в различных сценариях.
 *
 * @author MetaphysicsNecrosis
 * @version 1.0
 * @since 1.21.8
 */
public class FoodReplacementExample {

    private static final Logger LOGGER = LoggerFactory.getLogger(FoodReplacementExample.class);

    /**
     * Демонстрирует работу системы автозамены для различных типов еды
     */
    public static void demonstrateFoodReplacement(ServerLevel level) {
        LOGGER.info("=== Демонстрация системы автозамены еды ===");

        // Проверяем что система включена
        if (!Config.ENABLE_SPOILAGE_SYSTEM.get()) {
            LOGGER.warn("Система порчи отключена в конфигурации");
            return;
        }

        // Тестируем различные виды еды
        testFoodItem(Items.APPLE, level, "Яблоко");
        testFoodItem(Items.BREAD, level, "Хлеб");
        testFoodItem(Items.COOKED_BEEF, level, "Приготовленная говядина");
        testFoodItem(Items.MILK_BUCKET, level, "Ведро молока");
        testFoodItem(Items.CAKE, level, "Торт");

        // Тестируем стеки с несколькими предметами
        testStackItems(level);

        // Тестируем исключенные предметы
        testExcludedItems(level);

        // Показываем статистику
        LOGGER.info(FoodReplacementHandler.getDetailedStats());
    }

    /**
     * Тестирует отдельный предмет еды
     */
    private static void testFoodItem(net.minecraft.world.item.Item item, ServerLevel level, String name) {
        try {
            ItemStack originalStack = new ItemStack(item);
            String itemId = BuiltInRegistries.ITEM.getKey(item).toString();

            LOGGER.info("Тестируем {}: {}", name, itemId);

            // Проверяем может ли предмет быть заменен
            boolean eligible = FoodReplacementHandler.isItemEligibleForReplacement(originalStack);
            LOGGER.info("  Подходит для замены: {}", eligible);

            if (eligible) {
                // Выполняем принудительную замену
                ItemStack replacedStack = FoodReplacementHandler.forceProcessItemStack(originalStack, level);

                boolean hasTimestamp = SpoilageUtils.hasTimestamp(replacedStack);
                long creationDay = hasTimestamp ? SpoilageUtils.getCreationDay(replacedStack) : -1;

                LOGGER.info("  Временная метка установлена: {}", hasTimestamp);
                if (hasTimestamp) {
                    LOGGER.info("  День создания: {}", creationDay);

                    // Проверяем срок хранения
                    long daysUntilSpoilage = TimedFoodManager.getDaysUntilSpoilage(replacedStack, level);
                    LOGGER.info("  Дней до порчи: {}", daysUntilSpoilage);
                }
            } else {
                // Проверяем причину исключения
                if (Config.isItemExcluded(itemId)) {
                    LOGGER.info("  Причина: исключен в конфигурации");
                } else if (!SpoilageUtils.canItemSpoil(item)) {
                    LOGGER.info("  Причина: не может портиться");
                }
            }

            LOGGER.info(""); // Пустая строка для разделения

        } catch (Exception e) {
            LOGGER.error("Ошибка при тестировании {}: {}", name, e.getMessage());
        }
    }

    /**
     * Тестирует стеки с несколькими предметами
     */
    private static void testStackItems(ServerLevel level) {
        LOGGER.info("=== Тестирование стеков ===");

        try {
            // Создаем стек из 16 яблок
            ItemStack appleStack = new ItemStack(Items.APPLE, 16);
            LOGGER.info("Тестируем стек: {} x{}",
                BuiltInRegistries.ITEM.getKey(Items.APPLE), appleStack.getCount());

            // Обрабатываем стек
            ItemStack processedStack = FoodReplacementHandler.forceProcessItemStack(appleStack, level);

            boolean hasTimestamp = SpoilageUtils.hasTimestamp(processedStack);
            LOGGER.info("Временная метка на стеке: {}", hasTimestamp);

            if (hasTimestamp) {
                long creationDay = SpoilageUtils.getCreationDay(processedStack);
                LOGGER.info("День создания стека: {}", creationDay);
            }

            LOGGER.info("");

        } catch (Exception e) {
            LOGGER.error("Ошибка при тестировании стеков: {}", e.getMessage());
        }
    }

    /**
     * Тестирует исключенные предметы
     */
    private static void testExcludedItems(ServerLevel level) {
        LOGGER.info("=== Тестирование исключенных предметов ===");

        try {
            // Получаем список исключенных предметов из конфига
            var excludedItems = Config.EXCLUDED_ITEMS.get();

            if (excludedItems.isEmpty()) {
                LOGGER.info("Нет исключенных предметов в конфигурации");
                return;
            }

            // Тестируем известные исключенные предметы
            LOGGER.info("Количество исключенных предметов: {}", excludedItems.size());
            for (String excludedId : excludedItems) {
                LOGGER.info("Исключенный предмет: {}", excludedId);

                // Проверяем что предмет действительно исключен
                boolean isExcluded = Config.isItemExcluded(excludedId);
                LOGGER.info("  Статус исключения: {}", isExcluded);
            }

            LOGGER.info("");

        } catch (Exception e) {
            LOGGER.error("Ошибка при тестировании исключенных предметов: {}", e.getMessage());
        }
    }

    /**
     * Проверяет производительность системы замены
     */
    public static void performanceTest(ServerLevel level, int itemCount) {
        LOGGER.info("=== Тест производительности (обработка {} предметов) ===", itemCount);

        long startTime = System.nanoTime();

        try {
            for (int i = 0; i < itemCount; i++) {
                ItemStack testStack = new ItemStack(Items.APPLE);
                FoodReplacementHandler.forceProcessItemStack(testStack, level);
            }

            long endTime = System.nanoTime();
            long durationMs = (endTime - startTime) / 1_000_000;

            LOGGER.info("Обработано {} предметов за {} мс", itemCount, durationMs);
            LOGGER.info("Среднее время на предмет: {} мкс", (endTime - startTime) / itemCount / 1000);

            // Показываем статистику кэша
            LOGGER.info("Статистика кэша после теста:");
            LOGGER.info(FoodReplacementHandler.getReplacementStats());

        } catch (Exception e) {
            LOGGER.error("Ошибка в тесте производительности: {}", e.getMessage());
        }
    }

    /**
     * Валидирует всю систему автозамены
     */
    public static boolean validateSystem() {
        LOGGER.info("=== Валидация системы автозамены ===");

        try {
            // Проверяем основные компоненты
            boolean configValid = Config.ENABLE_SPOILAGE_SYSTEM != null;
            boolean timedFoodManagerValid = TimedFoodManager.validateManager();
            boolean handlerValid = FoodReplacementHandler.validateHandler();

            LOGGER.info("Конфигурация: {}", configValid ? "OK" : "ERROR");
            LOGGER.info("TimedFoodManager: {}", timedFoodManagerValid ? "OK" : "ERROR");
            LOGGER.info("FoodReplacementHandler: {}", handlerValid ? "OK" : "ERROR");

            boolean systemValid = configValid && timedFoodManagerValid && handlerValid;
            LOGGER.info("Система в целом: {}", systemValid ? "OK" : "ERROR");

            return systemValid;

        } catch (Exception e) {
            LOGGER.error("Ошибка валидации системы: {}", e.getMessage());
            return false;
        }
    }
}