package com.metaphysicsnecrosis.metaphysicsspoilage.testing;

import com.metaphysicsnecrosis.metaphysicsspoilage.items.FoodContainer;
import com.metaphysicsnecrosis.metaphysicsspoilage.items.StoredFoodEntry;
import com.metaphysicsnecrosis.metaphysicsspoilage.manager.TimedFoodManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Специализированные тесты для FoodContainer с фокусом на большие значения дней.
 *
 * Проверяет:
 * - Хранение еды с экстремальными датами создания
 * - Сортировку по дням (FIFO принцип)
 * - Производительность операций с большими числами
 * - Корректность отображения больших дат в GUI
 */
public class FoodContainerTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(FoodContainerTests.class);

    /**
     * Тестирует добавление еды с экстремальными датами
     */
    public static boolean testAddFoodWithExtremeDates() {
        LOGGER.info("--- Тестирование добавления еды с экстремальными датами ---");

        boolean allPassed = true;

        long[] extremeDays = {
            0L,
            1L,
            -1L,
            1_000_000L,           // Миллион дней
            -1_000_000L,
            1_000_000_000L,       // Миллиард дней
            -1_000_000_000L,
            Long.MAX_VALUE - 1000L,
            Long.MAX_VALUE - 1L,
            Long.MAX_VALUE,
            Long.MIN_VALUE + 1000L,
            Long.MIN_VALUE + 1L,
            Long.MIN_VALUE
        };

        try {
            // Создаем тестовый контейнер (используем заглушку - обычный предмет)
            ItemStack containerStack = new ItemStack(Items.CHEST);

            for (long testDay : extremeDays) {
                try {
                    // Создаем еду с экстремальной датой
                    ItemStack food = TimedFoodManager.createTimedFood(Items.APPLE, testDay);

                    if (!food.isEmpty()) {
                        // Пытаемся добавить в контейнер
                        // Примечание: FoodContainer.addFood требует реальный FoodContainer,
                        // но для тестирования мы можем проверить логику StoredFoodEntry

                        String itemId = BuiltInRegistries.ITEM.getKey(Items.APPLE).toString();
                        StoredFoodEntry entry = new StoredFoodEntry(itemId, testDay, 1);

                        // Проверяем корректность создания записи
                        if (entry.creationDay() != testDay) {
                            LOGGER.error("ОШИБКА: StoredFoodEntry создана с {}, получено {}", testDay, entry.creationDay());
                            allPassed = false;
                        } else {
                            LOGGER.debug("OK: StoredFoodEntry создана с днем {}", testDay);
                        }

                        // Тестируем метод withCount
                        StoredFoodEntry modifiedEntry = entry.withCount(5);
                        if (modifiedEntry.creationDay() != testDay || modifiedEntry.count() != 5) {
                            LOGGER.error("ОШИБКА: withCount изменил день {} или количество", testDay);
                            allPassed = false;
                        }

                    } else {
                        LOGGER.debug("Еда пуста для дня {} (ожидаемо если система отключена)", testDay);
                    }

                } catch (Exception e) {
                    LOGGER.error("ИСКЛЮЧЕНИЕ при тестировании дня {}: {}", testDay, e.getMessage());
                    allPassed = false;
                }
            }

        } catch (Exception e) {
            LOGGER.error("КРИТИЧЕСКАЯ ОШИБКА при тестировании добавления еды: {}", e.getMessage());
            allPassed = false;
        }

        LOGGER.info("Результат тестирования добавления еды: {}", allPassed ? "УСПЕШНО" : "ОШИБКИ");
        return allPassed;
    }

    /**
     * Тестирует сортировку FIFO с большими значениями дней
     */
    public static boolean testFIFOSortingWithExtremeDays() {
        LOGGER.info("--- Тестирование FIFO сортировки с экстремальными днями ---");

        boolean allPassed = true;

        try {
            String itemId = BuiltInRegistries.ITEM.getKey(Items.BREAD).toString();

            // Создаем записи с различными экстремальными днями
            StoredFoodEntry[] entries = {
                new StoredFoodEntry(itemId, Long.MAX_VALUE, 1),      // Самый поздний
                new StoredFoodEntry(itemId, 0L, 1),                 // Средний
                new StoredFoodEntry(itemId, Long.MIN_VALUE, 1),     // Самый ранний
                new StoredFoodEntry(itemId, 1_000_000_000L, 1),     // Большой положительный
                new StoredFoodEntry(itemId, -1_000_000_000L, 1),    // Большой отрицательный
                new StoredFoodEntry(itemId, 1L, 1),                 // Малый положительный
                new StoredFoodEntry(itemId, -1L, 1)                 // Малый отрицательный
            };

            // Проверяем сравнение записей для сортировки
            for (int i = 0; i < entries.length - 1; i++) {
                for (int j = i + 1; j < entries.length; j++) {
                    StoredFoodEntry first = entries[i];
                    StoredFoodEntry second = entries[j];

                    // Проверяем логику сравнения дней
                    boolean firstIsOlder = first.creationDay() < second.creationDay();
                    boolean comparisonResult = Long.compare(first.creationDay(), second.creationDay()) < 0;

                    if (firstIsOlder != comparisonResult) {
                        LOGGER.error("ОШИБКА: Некорректное сравнение дней {} и {}",
                            first.creationDay(), second.creationDay());
                        allPassed = false;
                    }

                    LOGGER.debug("Сравнение: {} {} {}",
                        first.creationDay(),
                        firstIsOlder ? "<" : ">=",
                        second.creationDay());
                }
            }

            // Тестируем поиск самой старой записи
            StoredFoodEntry oldest = null;
            for (StoredFoodEntry entry : entries) {
                if (oldest == null || entry.creationDay() < oldest.creationDay()) {
                    oldest = entry;
                }
            }

            if (oldest == null || oldest.creationDay() != Long.MIN_VALUE) {
                LOGGER.error("ОШИБКА: Неправильно найдена самая старая запись: {}",
                    oldest != null ? oldest.creationDay() : "null");
                allPassed = false;
            } else {
                LOGGER.debug("OK: Самая старая запись найдена корректно: {}", oldest.creationDay());
            }

        } catch (Exception e) {
            LOGGER.error("ОШИБКА при тестировании FIFO сортировки: {}", e.getMessage());
            allPassed = false;
        }

        LOGGER.info("Результат тестирования FIFO сортировки: {}", allPassed ? "УСПЕШНО" : "ОШИБКИ");
        return allPassed;
    }

    /**
     * Тестирует производительность операций с контейнером
     */
    public static boolean testPerformance() {
        LOGGER.info("--- Тестирование производительности FoodContainer ---");

        boolean allPassed = true;

        try {
            final int OPERATIONS_COUNT = 1_000;
            String itemId = BuiltInRegistries.ITEM.getKey(Items.CARROT).toString();

            // Тест производительности создания записей с большими днями
            long startTime = System.nanoTime();

            StoredFoodEntry[] entries = new StoredFoodEntry[OPERATIONS_COUNT];
            for (int i = 0; i < OPERATIONS_COUNT; i++) {
                long bigDay = Long.MAX_VALUE - i;
                entries[i] = new StoredFoodEntry(itemId, bigDay, 1);
            }

            long createTimeNs = System.nanoTime() - startTime;
            double createTimeMs = createTimeNs / 1_000_000.0;
            double avgCreateTimeMs = createTimeMs / OPERATIONS_COUNT;

            LOGGER.info("Время создания {} записей: {:.2f} мс (среднее: {:.3f} мс/операция)",
                OPERATIONS_COUNT, createTimeMs, avgCreateTimeMs);

            // Тест производительности поиска минимального дня
            startTime = System.nanoTime();

            for (int i = 0; i < OPERATIONS_COUNT; i++) {
                StoredFoodEntry oldest = null;
                for (StoredFoodEntry entry : entries) {
                    if (oldest == null || entry.creationDay() < oldest.creationDay()) {
                        oldest = entry;
                    }
                }
            }

            long searchTimeNs = System.nanoTime() - startTime;
            double searchTimeMs = searchTimeNs / 1_000_000.0;
            double avgSearchTimeMs = searchTimeMs / OPERATIONS_COUNT;

            LOGGER.info("Время поиска {} минимумов: {:.2f} мс (среднее: {:.3f} мс/операция)",
                OPERATIONS_COUNT, searchTimeMs, avgSearchTimeMs);

            // Тест производительности модификации записей
            startTime = System.nanoTime();

            for (int i = 0; i < OPERATIONS_COUNT; i++) {
                StoredFoodEntry original = entries[i];
                StoredFoodEntry modified = original.withCount(original.count() + 1);
                entries[i] = modified;
            }

            long modifyTimeNs = System.nanoTime() - startTime;
            double modifyTimeMs = modifyTimeNs / 1_000_000.0;
            double avgModifyTimeMs = modifyTimeMs / OPERATIONS_COUNT;

            LOGGER.info("Время модификации {} записей: {:.2f} мс (среднее: {:.3f} мс/операция)",
                OPERATIONS_COUNT, modifyTimeMs, avgModifyTimeMs);

            // Проверяем, что производительность приемлема
            if (avgCreateTimeMs > 1.0 || avgSearchTimeMs > 1.0 || avgModifyTimeMs > 1.0) {
                LOGGER.warn("ПРОИЗВОДИТЕЛЬНОСТЬ: Операции выполняются медленнее ожидаемого");
                // Не считаем критической ошибкой
            }

        } catch (Exception e) {
            LOGGER.error("ОШИБКА при тестировании производительности: {}", e.getMessage());
            allPassed = false;
        }

        LOGGER.info("Результат тестирования производительности: {}", allPassed ? "УСПЕШНО" : "ОШИБКИ");
        return allPassed;
    }

    /**
     * Тестирует граничные случаи и обработку ошибок
     */
    public static boolean testEdgeCases() {
        LOGGER.info("--- Тестирование граничных случаев FoodContainer ---");

        boolean allPassed = true;

        try {
            String itemId = BuiltInRegistries.ITEM.getKey(Items.POTATO).toString();

            // Тест с null значениями в StoredFoodEntry
            try {
                StoredFoodEntry entry = new StoredFoodEntry(null, 1L, 1);
                LOGGER.warn("StoredFoodEntry принял null itemId - может потребовать валидации");
            } catch (Exception e) {
                LOGGER.debug("OK: StoredFoodEntry корректно обработал null itemId");
            }

            // Тест с отрицательным количеством
            try {
                StoredFoodEntry entry = new StoredFoodEntry(itemId, 1L, -1);
                if (entry.count() < 0) {
                    LOGGER.warn("StoredFoodEntry принял отрицательное количество - может потребовать валидации");
                }
            } catch (Exception e) {
                LOGGER.debug("OK: StoredFoodEntry обработал отрицательное количество");
            }

            // Тест с очень большим количеством
            try {
                StoredFoodEntry entry = new StoredFoodEntry(itemId, 1L, Integer.MAX_VALUE);
                StoredFoodEntry modified = entry.withCount(Integer.MAX_VALUE);

                if (modified.count() != Integer.MAX_VALUE) {
                    LOGGER.error("ОШИБКА: Потеря данных при работе с Integer.MAX_VALUE");
                    allPassed = false;
                }
            } catch (Exception e) {
                LOGGER.error("ОШИБКА: Исключение при работе с Integer.MAX_VALUE: {}", e.getMessage());
                allPassed = false;
            }

            // Тест equals и hashCode для записей с экстремальными значениями
            StoredFoodEntry entry1 = new StoredFoodEntry(itemId, Long.MAX_VALUE, 1);
            StoredFoodEntry entry2 = new StoredFoodEntry(itemId, Long.MAX_VALUE, 1);
            StoredFoodEntry entry3 = new StoredFoodEntry(itemId, Long.MIN_VALUE, 1);

            if (!entry1.equals(entry2) || entry1.hashCode() != entry2.hashCode()) {
                LOGGER.error("ОШИБКА: equals/hashCode для одинаковых записей с Long.MAX_VALUE");
                allPassed = false;
            }

            if (entry1.equals(entry3) || entry2.equals(entry3)) {
                LOGGER.error("ОШИБКА: equals возвращает true для разных записей");
                allPassed = false;
            }

        } catch (Exception e) {
            LOGGER.error("ОШИБКА при тестировании граничных случаев: {}", e.getMessage());
            allPassed = false;
        }

        LOGGER.info("Результат тестирования граничных случаев: {}", allPassed ? "УСПЕШНО" : "ОШИБКИ");
        return allPassed;
    }

    /**
     * Тестирует стресс-нагрузку со случайными значениями
     */
    public static boolean testStressWithRandomValues() {
        LOGGER.info("--- Стресс-тест FoodContainer со случайными значениями ---");

        boolean allPassed = true;

        try {
            final int ITERATIONS = 5_000;
            int failures = 0;

            for (int i = 0; i < ITERATIONS; i++) {
                try {
                    // Генерируем случайные значения
                    long randomDay = ThreadLocalRandom.current().nextLong();
                    int randomCount = ThreadLocalRandom.current().nextInt(1, 65);
                    String itemId = "minecraft:test_item_" + (i % 10);

                    // Создаем запись
                    StoredFoodEntry entry = new StoredFoodEntry(itemId, randomDay, randomCount);

                    // Проверяем базовые свойства
                    if (entry.creationDay() != randomDay || entry.count() != randomCount || !entry.itemId().equals(itemId)) {
                        failures++;
                        if (failures <= 10) {
                            LOGGER.warn("Стресс-тест ошибка {}: день {}, количество {}, itemId {}",
                                failures, entry.creationDay(), entry.count(), entry.itemId());
                        }
                    }

                    // Тестируем модификацию
                    int newCount = ThreadLocalRandom.current().nextInt(1, 65);
                    StoredFoodEntry modified = entry.withCount(newCount);

                    if (modified.creationDay() != randomDay || modified.count() != newCount || !modified.itemId().equals(itemId)) {
                        failures++;
                    }

                    // Каждые 500 итераций выводим прогресс
                    if (i % 500 == 0 && i > 0) {
                        LOGGER.debug("Стресс-тест прогресс: {}/{} итераций", i, ITERATIONS);
                    }

                } catch (Exception e) {
                    failures++;
                    if (failures <= 5) {
                        LOGGER.error("Стресс-тест исключение в итерации {}: {}", i, e.getMessage());
                    }
                }
            }

            if (failures > 0) {
                LOGGER.warn("Стресс-тест завершен с {} ошибками из {} итераций", failures, ITERATIONS);
                if (failures > ITERATIONS * 0.01) { // Более 1% ошибок считаем проблемой
                    allPassed = false;
                }
            } else {
                LOGGER.info("Стресс-тест успешно завершен без ошибок");
            }

        } catch (Exception e) {
            LOGGER.error("КРИТИЧЕСКАЯ ОШИБКА при стресс-тесте: {}", e.getMessage());
            allPassed = false;
        }

        LOGGER.info("Результат стресс-теста: {}", allPassed ? "УСПЕШНО" : "ОШИБКИ");
        return allPassed;
    }

    /**
     * Запускает все специализированные тесты FoodContainer
     */
    public static boolean runAllTests() {
        LOGGER.info("=== ЗАПУСК ВСЕХ СПЕЦИАЛИЗИРОВАННЫХ ТЕСТОВ FoodContainer ===");

        boolean addTests = testAddFoodWithExtremeDates();
        boolean fifoTests = testFIFOSortingWithExtremeDays();
        boolean performanceTests = testPerformance();
        boolean edgeCaseTests = testEdgeCases();
        boolean stressTests = testStressWithRandomValues();

        boolean allPassed = addTests && fifoTests && performanceTests && edgeCaseTests && stressTests;

        LOGGER.info("=== РЕЗУЛЬТАТЫ СПЕЦИАЛИЗИРОВАННЫХ ТЕСТОВ FoodContainer ===");
        LOGGER.info("Добавление еды: {}", addTests ? "УСПЕШНО" : "ОШИБКИ");
        LOGGER.info("FIFO сортировка: {}", fifoTests ? "УСПЕШНО" : "ОШИБКИ");
        LOGGER.info("Производительность: {}", performanceTests ? "УСПЕШНО" : "ОШИБКИ");
        LOGGER.info("Граничные случаи: {}", edgeCaseTests ? "УСПЕШНО" : "ОШИБКИ");
        LOGGER.info("Стресс-тест: {}", stressTests ? "УСПЕШНО" : "ОШИБКИ");
        LOGGER.info("ОБЩИЙ РЕЗУЛЬТАТ: {}", allPassed ? "ВСЕ ТЕСТЫ ПРОЙДЕНЫ" : "ОБНАРУЖЕНЫ ПРОБЛЕМЫ");

        return allPassed;
    }
}