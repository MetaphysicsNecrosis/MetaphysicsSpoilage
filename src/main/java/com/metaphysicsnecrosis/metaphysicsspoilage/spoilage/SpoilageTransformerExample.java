package com.metaphysicsnecrosis.metaphysicsspoilage.spoilage;

import com.metaphysicsnecrosis.metaphysicsspoilage.Config;
import com.metaphysicsnecrosis.metaphysicsspoilage.manager.TimedFoodManager;
import com.metaphysicsnecrosis.metaphysicsspoilage.time.WorldDayTracker;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Пример использования системы превращения испорченных предметов.
 *
 * Демонстрирует основные функции SpoilageTransformer:
 * - Создание предметов с временной меткой
 * - Проверка порчи и автоматическое превращение
 * - Интеграция с конфигурацией
 * - Статистика превращений
 *
 * @author MetaphysicsNecrosis
 * @version 1.0
 * @since 1.21.8
 */
public class SpoilageTransformerExample {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpoilageTransformerExample.class);

    /**
     * Демонстрирует базовое превращение предмета
     */
    public static void demonstrateBasicTransformation() {
        LOGGER.info("=== ДЕМОНСТРАЦИЯ БАЗОВОГО ПРЕВРАЩЕНИЯ ===");

        // Создаем несколько предметов разных типов
        ItemStack apple = new ItemStack(Items.APPLE, 3);
        ItemStack beef = new ItemStack(Items.BEEF, 2);
        ItemStack bread = new ItemStack(Items.BREAD, 1);

        // Демонстрируем получение типов превращения
        SpoilageTransformer.SpoiledType appleType = SpoilageTransformer.getTransformationType(Items.APPLE);
        SpoilageTransformer.SpoiledType beefType = SpoilageTransformer.getTransformationType(Items.BEEF);
        SpoilageTransformer.SpoiledType breadType = SpoilageTransformer.getTransformationType(Items.BREAD);

        LOGGER.info("Типы превращения:");
        LOGGER.info("  Яблоко -> {} ({})", appleType.getName(),
                BuiltInRegistries.ITEM.getKey(appleType.getSpoiledItem()));
        LOGGER.info("  Говядина -> {} ({})", beefType.getName(),
                BuiltInRegistries.ITEM.getKey(beefType.getSpoiledItem()));
        LOGGER.info("  Хлеб -> {} ({})", breadType.getName(),
                BuiltInRegistries.ITEM.getKey(breadType.getSpoiledItem()));

        // Демонстрируем превращение предметов
        if (SpoilageTransformer.isTransformationEnabled()) {
            ItemStack spoiledApple = SpoilageTransformer.transformSpoiledItem(apple, Items.APPLE);
            ItemStack spoiledBeef = SpoilageTransformer.transformSpoiledItem(beef, Items.BEEF);
            ItemStack spoiledBread = SpoilageTransformer.transformSpoiledItem(bread, Items.BREAD);

            LOGGER.info("Результаты превращения:");
            LOGGER.info("  {} яблок -> {} {}",
                    apple.getCount(),
                    spoiledApple.getCount(),
                    BuiltInRegistries.ITEM.getKey(spoiledApple.getItem()));
            LOGGER.info("  {} говядины -> {} {}",
                    beef.getCount(),
                    spoiledBeef.getCount(),
                    BuiltInRegistries.ITEM.getKey(spoiledBeef.getItem()));
            LOGGER.info("  {} хлеба -> {} {}",
                    bread.getCount(),
                    spoiledBread.getCount(),
                    BuiltInRegistries.ITEM.getKey(spoiledBread.getItem()));
        } else {
            LOGGER.info("Превращение отключено в конфигурации");
        }

        LOGGER.info("=== КОНЕЦ ДЕМОНСТРАЦИИ ===\n");
    }

    /**
     * Демонстрирует работу с временными метками
     */
    public static void demonstrateTimedFoodTransformation(ServerLevel level) {
        LOGGER.info("=== ДЕМОНСТРАЦИЯ ПРЕВРАЩЕНИЯ ВРЕМЕННОЙ ЕДЫ ===");

        if (level == null) {
            LOGGER.warn("Невозможно продемонстрировать без ServerLevel");
            return;
        }

        WorldDayTracker tracker = WorldDayTracker.getInstance(level);
        long currentDay = tracker.getCurrentDay();

        LOGGER.info("Текущий игровой день: {}", currentDay);

        // Создаем предметы с временной меткой
        ItemStack timedApple = TimedFoodManager.createTimedFood(Items.APPLE, currentDay - 10); // старое яблоко
        ItemStack freshApple = TimedFoodManager.createTimedFood(Items.APPLE, currentDay);       // свежее яблоко

        LOGGER.info("Создан старый предмет: {} (день создания: {})",
                BuiltInRegistries.ITEM.getKey(timedApple.getItem()),
                SpoilageUtils.getCreationDay(timedApple));
        LOGGER.info("Создан свежий предмет: {} (день создания: {})",
                BuiltInRegistries.ITEM.getKey(freshApple.getItem()),
                SpoilageUtils.getCreationDay(freshApple));

        // Проверяем порчу
        boolean oldAppleSpoiled = SpoilageUtils.isItemSpoiled(timedApple, level);
        boolean freshAppleSpoiled = SpoilageUtils.isItemSpoiled(freshApple, level);

        LOGGER.info("Статус порчи:");
        LOGGER.info("  Старое яблоко испорчено: {}", oldAppleSpoiled);
        LOGGER.info("  Свежее яблоко испорчено: {}", freshAppleSpoiled);

        // Автоматическое превращение
        if (oldAppleSpoiled) {
            ItemStack transformedApple = SpoilageTransformer.autoTransformIfSpoiled(timedApple);
            if (!transformedApple.isEmpty()) {
                LOGGER.info("Старое яблоко автоматически превращено в: {}",
                        BuiltInRegistries.ITEM.getKey(transformedApple.getItem()));
            }
        }

        if (!freshAppleSpoiled) {
            ItemStack checkedFreshApple = SpoilageTransformer.autoTransformIfSpoiled(freshApple);
            LOGGER.info("Свежее яблоко остается: {} (без изменений: {})",
                    BuiltInRegistries.ITEM.getKey(checkedFreshApple.getItem()),
                    ItemStack.matches(freshApple, checkedFreshApple));
        }

        LOGGER.info("=== КОНЕЦ ДЕМОНСТРАЦИИ ===\n");
    }

    /**
     * Демонстрирует конфигурационные опции
     */
    public static void demonstrateConfigurationOptions() {
        LOGGER.info("=== ДЕМОНСТРАЦИЯ КОНФИГУРАЦИИ ===");

        // Показываем текущие настройки
        LOGGER.info("Система порчи включена: {}", Config.ENABLE_SPOILAGE_SYSTEM.get());
        LOGGER.info("Режим порчи: {}", Config.SPOILAGE_MODE.get().getName());
        LOGGER.info("Множитель скорости порчи: {}", Config.SPOILAGE_SPEED_MULTIPLIER.get());

        // Проверяем, включено ли превращение
        boolean transformationEnabled = SpoilageTransformer.isTransformationEnabled();
        LOGGER.info("Превращение включено: {}", transformationEnabled);

        if (transformationEnabled) {
            LOGGER.info("Превращение активно - испорченные предметы будут превращаться");
        } else {
            if (!Config.ENABLE_SPOILAGE_SYSTEM.get()) {
                LOGGER.info("Превращение отключено - система порчи отключена");
            } else if (Config.SPOILAGE_MODE.get() != Config.SpoilageMode.TRANSFORM_TO_SPOILED) {
                LOGGER.info("Превращение отключено - режим порчи: {}", Config.SPOILAGE_MODE.get().getName());
            }
        }

        // Демонстрируем проверку способности к превращению
        LOGGER.info("Способность к превращению:");
        LOGGER.info("  Яблоко: {}", SpoilageTransformer.canItemBeTransformed(Items.APPLE));
        LOGGER.info("  Говядина: {}", SpoilageTransformer.canItemBeTransformed(Items.BEEF));
        LOGGER.info("  Хлеб: {}", SpoilageTransformer.canItemBeTransformed(Items.BREAD));
        LOGGER.info("  Камень: {}", SpoilageTransformer.canItemBeTransformed(Items.STONE));

        LOGGER.info("=== КОНЕЦ ДЕМОНСТРАЦИИ ===\n");
    }

    /**
     * Демонстрирует статистику и отчеты
     */
    public static void demonstrateStatisticsAndReports() {
        LOGGER.info("=== ДЕМОНСТРАЦИЯ СТАТИСТИКИ ===");

        // Сначала выполним несколько превращений для генерации статистики
        for (int i = 0; i < 3; i++) {
            SpoilageTransformer.transformSpoiledItem(new ItemStack(Items.APPLE, 2), Items.APPLE);
            SpoilageTransformer.transformSpoiledItem(new ItemStack(Items.BEEF, 1), Items.BEEF);
        }

        // Показываем статистику
        var stats = SpoilageTransformer.getTransformationStatistics();
        LOGGER.info("Статистика превращений:");
        stats.forEach((item, count) ->
                LOGGER.info("  {}: {} превращений", item, count));

        // Генерируем полный отчет
        String report = SpoilageTransformer.generateTransformationReport();
        LOGGER.info("\nПолный отчет:\n{}", report);

        // Показываем информацию о кэше
        LOGGER.info("\nИнформация о кэше:");
        LOGGER.info("  {}", SpoilageTransformer.getCacheInfo());

        LOGGER.info("=== КОНЕЦ ДЕМОНСТРАЦИИ ===\n");
    }

    /**
     * Демонстрирует валидацию системы
     */
    public static void demonstrateValidation() {
        LOGGER.info("=== ДЕМОНСТРАЦИЯ ВАЛИДАЦИИ ===");

        // Валидируем трансформер
        boolean transformerValid = SpoilageTransformer.validateTransformer();
        LOGGER.info("Валидация SpoilageTransformer: {}", transformerValid ? "УСПЕШНО" : "НЕУДАЧНО");

        // Валидируем связанные системы
        boolean managerValid = TimedFoodManager.validateManager();
        LOGGER.info("Валидация TimedFoodManager: {}", managerValid ? "УСПЕШНО" : "НЕУДАЧНО");

        if (transformerValid && managerValid) {
            LOGGER.info("Все системы превращения работают корректно");
        } else {
            LOGGER.warn("Обнаружены проблемы в системах превращения");
        }

        LOGGER.info("=== КОНЕЦ ДЕМОНСТРАЦИИ ===\n");
    }

    /**
     * Запускает полную демонстрацию всех возможностей системы превращения
     */
    public static void runFullDemonstration(ServerLevel level) {
        LOGGER.info("НАЧАЛО ПОЛНОЙ ДЕМОНСТРАЦИИ СИСТЕМЫ ПРЕВРАЩЕНИЯ SPOILAGETRANSFORMER");
        LOGGER.info("=====================================================================");

        demonstrateBasicTransformation();
        demonstrateConfigurationOptions();
        demonstrateStatisticsAndReports();
        demonstrateValidation();

        if (level != null) {
            demonstrateTimedFoodTransformation(level);
        } else {
            LOGGER.warn("ServerLevel не предоставлен, пропускаем демонстрацию временной еды");
        }

        LOGGER.info("=====================================================================");
        LOGGER.info("КОНЕЦ ПОЛНОЙ ДЕМОНСТРАЦИИ СИСТЕМЫ ПРЕВРАЩЕНИЯ SPOILAGETRANSFORMER");
    }
}