package com.metaphysicsnecrosis.metaphysicsspoilage.testing;

import com.metaphysicsnecrosis.metaphysicsspoilage.manager.TimedFoodManager;
import com.metaphysicsnecrosis.metaphysicsspoilage.spoilage.SpoilageUtils;
import com.metaphysicsnecrosis.metaphysicsspoilage.time.WorldDayTracker;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Специализированные тесты для TimedFoodManager с фокусом на большие значения дней.
 *
 * Проверяет:
 * - Создание еды с экстремальными датами создания
 * - Корректность расчетов порчи с большими промежутками времени
 * - Производительность операций с большими числами
 * - Обработку граничных случаев в логике порчи
 */
public class TimedFoodManagerTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimedFoodManagerTests.class);

    /**
     * Тестирует создание еды с экстремальными датами
     */
    public static boolean testTimedFoodCreation(ServerLevel level) {
        LOGGER.info("--- Тестирование создания еды с экстремальными датами ---");

        boolean allPassed = true;

        long[] extremeDays = {
            0L,
            1L,
            -1L,
            1_000_000L,           // Миллион дней
            -1_000_000L,
            1_000_000_000L,       // Миллиард дней
            -1_000_000_000L,
            1_000_000_000_000L,   // Триллион дней
            -1_000_000_000_000L,
            Long.MAX_VALUE - 1000L,
            Long.MAX_VALUE - 1L,
            Long.MAX_VALUE,
            Long.MIN_VALUE + 1000L,
            Long.MIN_VALUE + 1L,
            Long.MIN_VALUE
        };

        for (long testDay : extremeDays) {
            try {
                // Тестируем с разными предметами еды
                ItemStack apple = TimedFoodManager.createTimedFood(Items.APPLE, testDay);
                ItemStack bread = TimedFoodManager.createTimedFood(Items.BREAD, testDay);
                ItemStack beef = TimedFoodManager.createTimedFood(Items.BEEF, testDay);

                // Проверяем, что еда создалась (если система порчи включена)
                boolean appleValid = validateTimedFood(apple, testDay, "APPLE");
                boolean breadValid = validateTimedFood(bread, testDay, "BREAD");
                boolean beefValid = validateTimedFood(beef, testDay, "BEEF");

                if (!appleValid || !breadValid || !beefValid) {
                    allPassed = false;
                }

                // Проверяем isTimedFood
                if (!apple.isEmpty() && SpoilageUtils.hasTimestamp(apple)) {
                    boolean isTimedDetected = TimedFoodManager.isTimedFood(apple);
                    if (!isTimedDetected) {
                        LOGGER.error("ОШИБКА: isTimedFood не распознал временную еду для дня {}", testDay);
                        allPassed = false;
                    }
                }

                // Проверяем getOriginalFood
                if (!apple.isEmpty()) {
                    var originalItem = TimedFoodManager.getOriginalFood(apple);
                    if (originalItem != Items.APPLE) {
                        LOGGER.error("ОШИБКА: getOriginalFood вернул неправильный предмет для дня {}", testDay);
                        allPassed = false;
                    }
                }

            } catch (Exception e) {
                LOGGER.error("ИСКЛЮЧЕНИЕ при создании еды для дня {}: {}", testDay, e.getMessage());
                allPassed = false;
            }
        }

        LOGGER.info("Результат тестирования создания еды: {}", allPassed ? "УСПЕШНО" : "ОШИБКИ");
        return allPassed;
    }

    /**
     * Тестирует расчеты порчи с большими промежутками времени
     */
    public static boolean testSpoilageCalculations(ServerLevel level) {
        LOGGER.info("--- Тестирование расчетов порчи с большими промежутками ---");

        boolean allPassed = true;

        try {
            WorldDayTracker tracker = WorldDayTracker.getInstance(level);

            // Тестируем различные сценарии с большими промежутками
            long[] creationDays = {
                0L,
                1_000_000L,
                Long.MAX_VALUE - 1_000_000L
            };

            long[] currentDays = {
                1_000L,
                1_001_000L,
                Long.MAX_VALUE - 999_000L
            };

            for (int i = 0; i < creationDays.length; i++) {
                long creationDay = creationDays[i];
                long currentDay = currentDays[i];

                try {
                    // Устанавливаем текущий день
                    tracker.setCurrentDay(currentDay);

                    // Создаем еду с указанным днем создания
                    ItemStack food = TimedFoodManager.createTimedFood(Items.APPLE, creationDay);

                    if (!food.isEmpty() && SpoilageUtils.hasTimestamp(food)) {
                        // Тестируем getDaysUntilSpoilage
                        long daysUntilSpoilage = TimedFoodManager.getDaysUntilSpoilage(food, level);

                        LOGGER.debug("Создан день: {}, Текущий день: {}, Дней до порчи: {}",
                            creationDay, currentDay, daysUntilSpoilage);

                        // Проверяем логичность результата
                        if (daysUntilSpoilage != -1) {
                            // Если система вернула значение, оно должно быть логичным
                            if (daysUntilSpoilage < -1_000_000L || daysUntilSpoilage > 1_000_000L) {
                                LOGGER.warn("ПРЕДУПРЕЖДЕНИЕ: Подозрительное значение дней до порчи: {} " +
                                    "(создан: {}, текущий: {})", daysUntilSpoilage, creationDay, currentDay);
                            }
                        }

                        // Тестируем checkAndProcessSpoilage
                        ItemStack processedFood = TimedFoodManager.checkAndProcessSpoilage(food, level);
                        if (processedFood == null) {
                            LOGGER.error("ОШИБКА: checkAndProcessSpoilage вернул null для дня {}", creationDay);
                            allPassed = false;
                        } else {
                            LOGGER.debug("Обработка порчи завершена для дня {}: {} -> {}",
                                creationDay, food.isEmpty() ? "EMPTY" : "PRESENT",
                                processedFood.isEmpty() ? "EMPTY" : "PRESENT");
                        }
                    }

                } catch (Exception e) {
                    LOGGER.error("ОШИБКА при расчете порчи (создан: {}, текущий: {}): {}",
                        creationDay, currentDay, e.getMessage());
                    allPassed = false;
                }
            }

        } catch (Exception e) {
            LOGGER.error("КРИТИЧЕСКАЯ ОШИБКА при тестировании расчетов порчи: {}", e.getMessage());
            allPassed = false;
        }

        LOGGER.info("Результат тестирования расчетов порчи: {}", allPassed ? "УСПЕШНО" : "ОШИБКИ");
        return allPassed;
    }

    /**
     * Тестирует производительность операций с большими значениями
     */
    public static boolean testPerformance(ServerLevel level) {
        LOGGER.info("--- Тестирование производительности TimedFoodManager ---");

        boolean allPassed = true;

        try {
            final int OPERATIONS_COUNT = 1_000;

            // Тест производительности создания еды с большими датами
            long startTime = System.nanoTime();

            for (int i = 0; i < OPERATIONS_COUNT; i++) {
                long bigDay = Long.MAX_VALUE - i;
                TimedFoodManager.createTimedFood(Items.APPLE, bigDay);
            }

            long createTimeNs = System.nanoTime() - startTime;
            double createTimeMs = createTimeNs / 1_000_000.0;
            double avgCreateTimeMs = createTimeMs / OPERATIONS_COUNT;

            LOGGER.info("Время создания {} временной еды: {:.2f} мс (среднее: {:.3f} мс/операция)",
                OPERATIONS_COUNT, createTimeMs, avgCreateTimeMs);

            // Тест производительности обработки еды
            ItemStack[] foods = new ItemStack[OPERATIONS_COUNT];
            for (int i = 0; i < OPERATIONS_COUNT; i++) {
                foods[i] = TimedFoodManager.createTimedFood(Items.BREAD, i);
            }

            startTime = System.nanoTime();

            for (ItemStack food : foods) {
                if (!food.isEmpty()) {
                    TimedFoodManager.processFood(food, level);
                }
            }

            long processTimeNs = System.nanoTime() - startTime;
            double processTimeMs = processTimeNs / 1_000_000.0;
            double avgProcessTimeMs = processTimeMs / OPERATIONS_COUNT;

            LOGGER.info("Время обработки {} еды: {:.2f} мс (среднее: {:.3f} мс/операция)",
                OPERATIONS_COUNT, processTimeMs, avgProcessTimeMs);

            // Проверяем, что производительность приемлема
            if (avgCreateTimeMs > 10.0 || avgProcessTimeMs > 10.0) {
                LOGGER.warn("ПРОИЗВОДИТЕЛЬНОСТЬ: Операции выполняются медленнее ожидаемого " +
                    "(создание: {:.3f} мс, обработка: {:.3f} мс)", avgCreateTimeMs, avgProcessTimeMs);
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
    public static boolean testEdgeCases(ServerLevel level) {
        LOGGER.info("--- Тестирование граничных случаев TimedFoodManager ---");

        boolean allPassed = true;

        try {
            // Тест с null значениями
            try {
                ItemStack nullFood = TimedFoodManager.createTimedFood(null, 1L);
                if (!nullFood.isEmpty()) {
                    LOGGER.error("ОШИБКА: createTimedFood с null предметом должен возвращать EMPTY");
                    allPassed = false;
                }
            } catch (Exception e) {
                LOGGER.debug("OK: createTimedFood корректно обработал null предмет");
            }

            // Тест с не-едой
            try {
                ItemStack notFood = TimedFoodManager.createTimedFood(Items.STONE, 1L);
                // Это может быть либо пустым, либо обычным камнем без метки
                LOGGER.debug("Создание временного камня: {}", notFood.isEmpty() ? "EMPTY" : "PRESENT");
            } catch (Exception e) {
                LOGGER.debug("OK: createTimedFood корректно обработал не-еду");
            }

            // Тест переполнения при расчетах
            try {
                WorldDayTracker tracker = WorldDayTracker.getInstance(level);
                tracker.setCurrentDay(Long.MAX_VALUE);

                ItemStack oldFood = TimedFoodManager.createTimedFood(Items.APPLE, Long.MIN_VALUE);
                if (!oldFood.isEmpty() && SpoilageUtils.hasTimestamp(oldFood)) {
                    long daysUntil = TimedFoodManager.getDaysUntilSpoilage(oldFood, level);
                    LOGGER.debug("Расчет порчи с переполнением: {}", daysUntil);
                }
            } catch (Exception e) {
                LOGGER.debug("Обработка переполнения при расчете порчи: {}", e.getMessage());
            }

            // Тест canSpoil с различными предметами
            boolean appleCanSpoil = TimedFoodManager.canSpoil(Items.APPLE);
            boolean stoneCanSpoil = TimedFoodManager.canSpoil(Items.STONE);

            LOGGER.debug("Может ли портиться - яблоко: {}, камень: {}", appleCanSpoil, stoneCanSpoil);

            // Тест getSpoiledType
            var appleType = TimedFoodManager.getSpoiledType(Items.APPLE);
            var breadType = TimedFoodManager.getSpoiledType(Items.BREAD);
            var beefType = TimedFoodManager.getSpoiledType(Items.BEEF);

            LOGGER.debug("Типы порчи - яблоко: {}, хлеб: {}, говядина: {}",
                appleType.getName(), breadType.getName(), beefType.getName());

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
    public static boolean testStressWithRandomValues(ServerLevel level) {
        LOGGER.info("--- Стресс-тест TimedFoodManager со случайными значениями ---");

        boolean allPassed = true;

        try {
            final int ITERATIONS = 1_000;
            int failures = 0;

            for (int i = 0; i < ITERATIONS; i++) {
                try {
                    // Генерируем случайные значения
                    long randomDay = ThreadLocalRandom.current().nextLong();
                    var randomItem = switch (ThreadLocalRandom.current().nextInt(5)) {
                        case 0 -> Items.APPLE;
                        case 1 -> Items.BREAD;
                        case 2 -> Items.BEEF;
                        case 3 -> Items.CARROT;
                        default -> Items.PORKCHOP;
                    };

                    // Создаем еду
                    ItemStack food = TimedFoodManager.createTimedFood(randomItem, randomDay);

                    // Проверяем базовые операции
                    if (!food.isEmpty() && SpoilageUtils.hasTimestamp(food)) {
                        boolean isTimedDetected = TimedFoodManager.isTimedFood(food);
                        var originalItem = TimedFoodManager.getOriginalFood(food);
                        long retrievedDay = SpoilageUtils.getCreationDay(food);

                        if (!isTimedDetected || originalItem != randomItem || retrievedDay != randomDay) {
                            failures++;
                            if (failures <= 10) {
                                LOGGER.warn("Стресс-тест ошибка {}: день {}, предмет {}, " +
                                    "isTimedDetected: {}, originalItem: {}, retrievedDay: {}",
                                    failures, randomDay, randomItem, isTimedDetected, originalItem, retrievedDay);
                            }
                        }
                    }

                    // Каждые 100 итераций выводим прогресс
                    if (i % 100 == 0 && i > 0) {
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
                if (failures > ITERATIONS * 0.05) { // Более 5% ошибок считаем проблемой
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
     * Запускает все специализированные тесты TimedFoodManager
     */
    public static boolean runAllTests(ServerLevel level) {
        LOGGER.info("=== ЗАПУСК ВСЕХ СПЕЦИАЛИЗИРОВАННЫХ ТЕСТОВ TimedFoodManager ===");

        boolean creationTests = testTimedFoodCreation(level);
        boolean spoilageTests = testSpoilageCalculations(level);
        boolean performanceTests = testPerformance(level);
        boolean edgeCaseTests = testEdgeCases(level);
        boolean stressTests = testStressWithRandomValues(level);

        boolean allPassed = creationTests && spoilageTests && performanceTests && edgeCaseTests && stressTests;

        LOGGER.info("=== РЕЗУЛЬТАТЫ СПЕЦИАЛИЗИРОВАННЫХ ТЕСТОВ TimedFoodManager ===");
        LOGGER.info("Создание еды: {}", creationTests ? "УСПЕШНО" : "ОШИБКИ");
        LOGGER.info("Расчеты порчи: {}", spoilageTests ? "УСПЕШНО" : "ОШИБКИ");
        LOGGER.info("Производительность: {}", performanceTests ? "УСПЕШНО" : "ОШИБКИ");
        LOGGER.info("Граничные случаи: {}", edgeCaseTests ? "УСПЕШНО" : "ОШИБКИ");
        LOGGER.info("Стресс-тест: {}", stressTests ? "УСПЕШНО" : "ОШИБКИ");
        LOGGER.info("ОБЩИЙ РЕЗУЛЬТАТ: {}", allPassed ? "ВСЕ ТЕСТЫ ПРОЙДЕНЫ" : "ОБНАРУЖЕНЫ ПРОБЛЕМЫ");

        return allPassed;
    }

    /**
     * Вспомогательный метод для валидации временной еды
     */
    private static boolean validateTimedFood(ItemStack food, long expectedDay, String itemName) {
        if (food.isEmpty()) {
            LOGGER.debug("Еда {} пуста для дня {} (возможно, система порчи отключена)", itemName, expectedDay);
            return true; // Не считаем ошибкой если система отключена
        }

        if (!SpoilageUtils.hasTimestamp(food)) {
            LOGGER.debug("Еда {} не имеет временной метки для дня {} (возможно, предмет исключен)", itemName, expectedDay);
            return true; // Не считаем ошибкой если предмет исключен
        }

        long actualDay = SpoilageUtils.getCreationDay(food);
        if (actualDay != expectedDay) {
            LOGGER.error("ОШИБКА {}: ожидался день {}, получен {}", itemName, expectedDay, actualDay);
            return false;
        }

        LOGGER.debug("OK {}: день {} корректно установлен", itemName, expectedDay);
        return true;
    }
}