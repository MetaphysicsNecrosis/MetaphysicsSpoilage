package com.metaphysicsnecrosis.metaphysicsspoilage.spoilage;

import com.metaphysicsnecrosis.metaphysicsspoilage.manager.TimedFoodManager;
import com.metaphysicsnecrosis.metaphysicsspoilage.time.WorldDayTracker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Класс-пример демонстрации использования системы SpoilageChecker.
 *
 * Показывает основные сценарии использования:
 * - Проверка отдельных предметов на порчу
 * - Работа с контейнерами и инвентарями
 * - Массовая обработка предметов
 * - Использование статистики и отчетов
 * - Интеграция с существующими системами
 *
 * @author MetaphysicsNecrosis
 * @version 1.0
 * @since 1.21.8
 */
public class SpoilageCheckerExample {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpoilageCheckerExample.class);

    /**
     * Демонстрирует основные возможности SpoilageChecker.
     * Этот метод можно вызвать из команды или события для тестирования.
     *
     * @param level Серверный уровень
     * @param player Игрок для демонстрации (может быть null для некоторых примеров)
     */
    public static void demonstrateBasicUsage(ServerLevel level, ServerPlayer player) {
        LOGGER.info("=== ДЕМОНСТРАЦИЯ СИСТЕМЫ SPOILAGE CHECKER ===");

        // === 1. СОЗДАНИЕ ТЕСТОВЫХ ПРЕДМЕТОВ ===
        LOGGER.info("1. Создание тестовых предметов с разными сроками годности");

        WorldDayTracker dayTracker = WorldDayTracker.getInstance(level);
        long currentDay = dayTracker.getCurrentDay();

        // Создаем свежие предметы
        ItemStack freshApple = TimedFoodManager.createTimedFood(Items.APPLE, currentDay);
        ItemStack freshBread = TimedFoodManager.createTimedFood(Items.BREAD, currentDay);
        ItemStack freshMeat = TimedFoodManager.createTimedFood(Items.COOKED_BEEF, currentDay);

        // Создаем предметы разного возраста
        ItemStack oldApple = TimedFoodManager.createTimedFood(Items.APPLE, currentDay - 3); // Возраст 3 дня
        ItemStack oldBread = TimedFoodManager.createTimedFood(Items.BREAD, currentDay - 5); // Возраст 5 дней
        ItemStack oldMeat = TimedFoodManager.createTimedFood(Items.COOKED_BEEF, currentDay - 1); // Возраст 1 день

        LOGGER.info("Созданы предметы: свежие (день {}) и старые (дни {}, {}, {})",
                currentDay, currentDay - 3, currentDay - 5, currentDay - 1);

        // === 2. ПРОВЕРКА ОТДЕЛЬНЫХ ПРЕДМЕТОВ ===
        LOGGER.info("2. Проверка отдельных предметов на порчу");

        demonstrateItemChecking(level, freshApple, "Свежее яблоко");
        demonstrateItemChecking(level, oldApple, "Старое яблоко");
        demonstrateItemChecking(level, freshBread, "Свежий хлеб");
        demonstrateItemChecking(level, oldBread, "Старый хлеб");
        demonstrateItemChecking(level, freshMeat, "Свежее мясо");
        demonstrateItemChecking(level, oldMeat, "Старое мясо");

        // === 3. РАБОТА С КОНТЕЙНЕРАМИ ===
        LOGGER.info("3. Демонстрация работы с контейнерами");

        Container testContainer = createTestContainer(currentDay, level);
        LOGGER.info("Создан тестовый контейнер с {} предметами", getContainerItemCount(testContainer));

        // Проверяем контейнер до обработки
        LOGGER.info("Содержимое контейнера ДО проверки:");
        logContainerContents(testContainer, level);

        // Обрабатываем контейнер
        SpoilageChecker.checkContainerForSpoilage(testContainer, level);

        // Проверяем контейнер после обработки
        LOGGER.info("Содержимое контейнера ПОСЛЕ проверки:");
        logContainerContents(testContainer, level);

        // === 4. РАБОТА С ИНВЕНТАРЕМ ИГРОКА ===
        if (player != null) {
            LOGGER.info("4. Демонстрация работы с инвентарем игрока");

            // Добавляем тестовые предметы в инвентарь игрока
            addTestItemsToPlayer(player, currentDay, level);

            LOGGER.info("Проверка инвентаря игрока {} до обработки:", player.getName().getString());
            logPlayerInventory(player, level);

            // Обрабатываем инвентарь игрока
            SpoilageChecker.checkPlayerInventory(player, level);

            LOGGER.info("Проверка инвентаря игрока {} после обработки:", player.getName().getString());
            logPlayerInventory(player, level);
        }

        // === 5. МАССОВАЯ ОБРАБОТКА ===
        LOGGER.info("5. Демонстрация массовой обработки контейнеров");

        List<Container> containers = createMultipleTestContainers(currentDay, level);
        LOGGER.info("Создано {} тестовых контейнеров для массовой обработки", containers.size());

        SpoilageChecker.checkMultipleContainers(containers, level);

        // === 6. СТАТИСТИКА И ОТЧЕТЫ ===
        LOGGER.info("6. Демонстрация статистики и отчетов");

        SpoilageChecker.SpoilageStatistics stats = SpoilageChecker.getStatistics();
        LOGGER.info("Текущая статистика системы порчи:");
        LOGGER.info("  - Общих проверок: {}", stats.getTotalChecks());
        LOGGER.info("  - Испорченных предметов: {}", stats.getSpoiledItems());
        LOGGER.info("  - Проверок контейнеров: {}", stats.getContainerChecks());
        LOGGER.info("  - Проверок инвентарей: {}", stats.getInventoryChecks());
        LOGGER.info("  - Эффективность кэша: {:.2f}%", stats.getCacheHitRatio() * 100);

        // Генерируем полный отчет
        String fullReport = SpoilageChecker.generateReport();
        LOGGER.info("Полный отчет системы порчи:\n{}", fullReport);

        // === 7. ИНФОРМАЦИЯ О КЭШЕ ===
        LOGGER.info("7. Информация о состоянии кэшей");
        String cacheInfo = SpoilageChecker.getCacheInfo();
        LOGGER.info("Состояние кэшей: {}", cacheInfo);

        LOGGER.info("=== ДЕМОНСТРАЦИЯ ЗАВЕРШЕНА ===");
    }

    /**
     * Демонстрирует проверку отдельного предмета на порчу.
     */
    private static void demonstrateItemChecking(ServerLevel level, ItemStack itemStack, String description) {
        if (itemStack.isEmpty()) {
            LOGGER.info("  {} - пустой предмет", description);
            return;
        }

        boolean isSpoiled = SpoilageChecker.isItemSpoiled(itemStack, level);
        long timeUntilSpoilage = SpoilageChecker.getTimeUntilSpoilage(itemStack, level);

        String status = isSpoiled ? "ИСПОРЧЕН" : "свежий";
        String timeInfo = timeUntilSpoilage >= 0 ?
                String.format("(дней до порчи: %d)", timeUntilSpoilage) :
                "(не портится)";

        LOGGER.info("  {} - {} {}", description, status, timeInfo);

        // Демонстрируем обработку порчи
        if (isSpoiled) {
            ItemStack processed = SpoilageChecker.processSpoilage(itemStack, level);
            if (processed.isEmpty()) {
                LOGGER.info("    Результат обработки: предмет исчез");
            } else {
                String processedName = processed.getDisplayName().getString();
                LOGGER.info("    Результат обработки: превращен в {}", processedName);
            }
        }
    }

    /**
     * Создает тестовый контейнер с предметами разного возраста.
     */
    private static Container createTestContainer(long currentDay, ServerLevel level) {
        SimpleContainer container = new SimpleContainer(27); // Размер сундука

        // Добавляем различные предметы
        container.setItem(0, TimedFoodManager.createTimedFood(Items.APPLE, currentDay));
        container.setItem(1, TimedFoodManager.createTimedFood(Items.APPLE, currentDay - 6)); // Испорченное
        container.setItem(2, TimedFoodManager.createTimedFood(Items.BREAD, currentDay - 1));
        container.setItem(3, TimedFoodManager.createTimedFood(Items.BREAD, currentDay - 4)); // Испорченный
        container.setItem(4, TimedFoodManager.createTimedFood(Items.COOKED_BEEF, currentDay));
        container.setItem(5, TimedFoodManager.createTimedFood(Items.COOKED_BEEF, currentDay - 3)); // Испорченное
        container.setItem(6, TimedFoodManager.createTimedFood(Items.COOKED_CHICKEN, currentDay - 1));
        container.setItem(7, new ItemStack(Items.DIAMOND)); // Неедовой предмет
        container.setItem(8, TimedFoodManager.createTimedFood(Items.MILK_BUCKET, currentDay - 2)); // Испорченное

        return container;
    }

    /**
     * Создает несколько тестовых контейнеров для массовой обработки.
     */
    private static List<Container> createMultipleTestContainers(long currentDay, ServerLevel level) {
        List<Container> containers = new ArrayList<>();

        for (int i = 0; i < 3; i++) {
            SimpleContainer container = new SimpleContainer(9);

            // Заполняем каждый контейнер разными предметами
            container.setItem(0, TimedFoodManager.createTimedFood(Items.APPLE, currentDay - i));
            container.setItem(1, TimedFoodManager.createTimedFood(Items.BREAD, currentDay - (i + 2)));
            container.setItem(2, TimedFoodManager.createTimedFood(Items.COOKED_BEEF, currentDay - (i + 1)));

            containers.add(container);
        }

        return containers;
    }

    /**
     * Добавляет тестовые предметы в инвентарь игрока.
     */
    private static void addTestItemsToPlayer(ServerPlayer player, long currentDay, ServerLevel level) {
        // Очищаем несколько слотов и добавляем тестовые предметы
        for (int i = 0; i < 5; i++) {
            if (player.getInventory().getItem(i).isEmpty()) {
                ItemStack testItem;
                switch (i) {
                    case 0:
                        testItem = TimedFoodManager.createTimedFood(Items.APPLE, currentDay);
                        break;
                    case 1:
                        testItem = TimedFoodManager.createTimedFood(Items.APPLE, currentDay - 6); // Испорченное
                        break;
                    case 2:
                        testItem = TimedFoodManager.createTimedFood(Items.BREAD, currentDay - 1);
                        break;
                    case 3:
                        testItem = TimedFoodManager.createTimedFood(Items.COOKED_BEEF, currentDay - 3); // Испорченное
                        break;
                    default:
                        testItem = new ItemStack(Items.DIAMOND); // Неедовой предмет
                        break;
                }
                player.getInventory().setItem(i, testItem);
            }
        }
    }

    /**
     * Логирует содержимое контейнера.
     */
    private static void logContainerContents(Container container, ServerLevel level) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty()) {
                String itemName = stack.getDisplayName().getString();
                boolean spoiled = SpoilageChecker.isItemSpoiled(stack, level);
                long timeUntil = SpoilageChecker.getTimeUntilSpoilage(stack, level);

                String statusInfo = spoiled ? " [ИСПОРЧЕН]" :
                        (timeUntil >= 0 ? String.format(" [%d дней до порчи]", timeUntil) : " [не портится]");

                LOGGER.info("    Слот {}: {} x{}{}", i, itemName, stack.getCount(), statusInfo);
            }
        }
    }

    /**
     * Логирует содержимое инвентаря игрока.
     */
    private static void logPlayerInventory(Player player, ServerLevel level) {
        for (int i = 0; i < Math.min(9, player.getInventory().getContainerSize()); i++) { // Только хотбар
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                String itemName = stack.getDisplayName().getString();
                boolean spoiled = SpoilageChecker.isItemSpoiled(stack, level);
                long timeUntil = SpoilageChecker.getTimeUntilSpoilage(stack, level);

                String statusInfo = spoiled ? " [ИСПОРЧЕН]" :
                        (timeUntil >= 0 ? String.format(" [%d дней до порчи]", timeUntil) : " [не портится]");

                LOGGER.info("    Хотбар слот {}: {} x{}{}", i, itemName, stack.getCount(), statusInfo);
            }
        }
    }

    /**
     * Подсчитывает количество предметов в контейнере.
     */
    private static int getContainerItemCount(Container container) {
        int count = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (!container.getItem(i).isEmpty()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Демонстрирует расширенное использование системы порчи.
     * Включает продвинутые сценарии и интеграцию.
     */
    public static void demonstrateAdvancedUsage(ServerLevel level) {
        LOGGER.info("=== ДЕМОНСТРАЦИЯ РАСШИРЕННОГО ИСПОЛЬЗОВАНИЯ ===");

        // === 1. ВАЛИДАЦИЯ СИСТЕМЫ ===
        LOGGER.info("1. Валидация корректности работы системы");
        boolean isValid = SpoilageChecker.validate(level);
        LOGGER.info("Результат валидации: {}", isValid ? "УСПЕШНО" : "ОШИБКА");

        // === 2. РАБОТА С КЭШЕМ ===
        LOGGER.info("2. Демонстрация работы с кэшем");

        // Создаем предметы для тестирования кэша
        ItemStack testItem = TimedFoodManager.createTimedFood(Items.APPLE, WorldDayTracker.getInstance(level).getCurrentDay() - 2);

        // Выполняем несколько проверок для демонстрации кэширования
        for (int i = 0; i < 5; i++) {
            boolean spoiled = SpoilageChecker.isItemSpoiled(testItem, level);
            long timeUntil = SpoilageChecker.getTimeUntilSpoilage(testItem, level);
        }

        SpoilageChecker.SpoilageStatistics stats = SpoilageChecker.getStatistics();
        LOGGER.info("Статистика кэша после тестирования:");
        LOGGER.info("  - Попаданий в кэш: {}", stats.getCacheHits());
        LOGGER.info("  - Промахов кэша: {}", stats.getCacheMisses());
        LOGGER.info("  - Эффективность: {:.2f}%", stats.getCacheHitRatio() * 100);

        // === 3. СБРОС И ОЧИСТКА ===
        LOGGER.info("3. Демонстрация сброса и очистки");

        LOGGER.info("Размеры кэшей до очистки: {}", SpoilageChecker.getCacheInfo());
        SpoilageChecker.clearCache();
        LOGGER.info("Размеры кэшей после очистки: {}", SpoilageChecker.getCacheInfo());

        LOGGER.info("Статистика до сброса: {} проверок", SpoilageChecker.getStatistics().getTotalChecks());
        SpoilageChecker.resetStatistics();
        LOGGER.info("Статистика после сброса: {} проверок", SpoilageChecker.getStatistics().getTotalChecks());

        // === 4. ИНТЕГРАЦИЯ С ДРУГИМИ СИСТЕМАМИ ===
        LOGGER.info("4. Демонстрация интеграции с TimedFoodManager");

        // Создаем предметы через TimedFoodManager
        ItemStack timedFood = TimedFoodManager.createTimedFood(Items.BREAD, WorldDayTracker.getInstance(level).getCurrentDay() - 5);
        LOGGER.info("Создан предмет через TimedFoodManager: {}", timedFood.getDisplayName().getString());

        // Проверяем его через SpoilageChecker
        boolean isTimedFoodSpoiled = SpoilageChecker.isItemSpoiled(timedFood, level);
        LOGGER.info("Результат проверки через SpoilageChecker: {}", isTimedFoodSpoiled ? "испорчен" : "свежий");

        // Обрабатываем через TimedFoodManager
        ItemStack processedByManager = TimedFoodManager.checkAndProcessSpoilage(timedFood, level);
        LOGGER.info("Результат обработки через TimedFoodManager: {}",
                processedByManager.isEmpty() ? "удален" : processedByManager.getDisplayName().getString());

        LOGGER.info("=== РАСШИРЕННАЯ ДЕМОНСТРАЦИЯ ЗАВЕРШЕНА ===");
    }

    /**
     * Выполняет стресс-тест системы порчи для проверки производительности.
     */
    public static void performStressTest(ServerLevel level) {
        LOGGER.info("=== СТРЕСС-ТЕСТ СИСТЕМЫ SPOILAGE CHECKER ===");

        long startTime = System.currentTimeMillis();
        int itemCount = 1000;
        long currentDay = WorldDayTracker.getInstance(level).getCurrentDay();

        // Создаем большое количество предметов
        List<ItemStack> testItems = new ArrayList<>();
        for (int i = 0; i < itemCount; i++) {
            ItemStack item = TimedFoodManager.createTimedFood(Items.APPLE, currentDay - (i % 10));
            testItems.add(item);
        }

        LOGGER.info("Создано {} тестовых предметов за {} мс",
                itemCount, System.currentTimeMillis() - startTime);

        // Выполняем массовую проверку
        long checkStartTime = System.currentTimeMillis();
        int spoiledCount = 0;

        for (ItemStack item : testItems) {
            if (SpoilageChecker.isItemSpoiled(item, level)) {
                spoiledCount++;
            }
        }

        long checkEndTime = System.currentTimeMillis();
        LOGGER.info("Проверено {} предметов за {} мс (найдено {} испорченных)",
                itemCount, checkEndTime - checkStartTime, spoiledCount);

        // Проверяем эффективность кэша
        SpoilageChecker.SpoilageStatistics finalStats = SpoilageChecker.getStatistics();
        LOGGER.info("Финальная эффективность кэша: {:.2f}%", finalStats.getCacheHitRatio() * 100);

        long totalTime = System.currentTimeMillis() - startTime;
        LOGGER.info("Общее время стресс-теста: {} мс", totalTime);
        LOGGER.info("Производительность: {:.2f} проверок/мс", (double) itemCount / totalTime);

        LOGGER.info("=== СТРЕСС-ТЕСТ ЗАВЕРШЕН ===");
    }
}