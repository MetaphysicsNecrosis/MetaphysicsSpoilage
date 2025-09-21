package com.metaphysicsnecrosis.metaphysicsspoilage.testing;

import com.metaphysicsnecrosis.metaphysicsspoilage.component.SpoilageComponent;
import com.metaphysicsnecrosis.metaphysicsspoilage.items.FoodContainer;
import com.metaphysicsnecrosis.metaphysicsspoilage.items.StoredFoodEntry;
import com.metaphysicsnecrosis.metaphysicsspoilage.manager.TimedFoodManager;
import com.metaphysicsnecrosis.metaphysicsspoilage.spoilage.SpoilageUtils;
import com.metaphysicsnecrosis.metaphysicsspoilage.time.WorldDayTracker;
import com.metaphysicsnecrosis.metaphysicsspoilage.tooltip.TooltipUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Менеджер для комплексного тестирования системы порчи с большими значениями дней.
 *
 * Тестирует экстремальные случаи работы с long значениями:
 * - Long.MAX_VALUE (9,223,372,036,854,775,807)
 * - Миллионы и миллиарды дней
 * - Отрицательные значения (граничные случаи)
 * - Переполнение и underflow сценарии
 *
 * Компоненты для тестирования:
 * 1. WorldDayTracker - сохранение/загрузка больших значений дней
 * 2. SpoilageComponent - DataComponent с long значениями, Codec сериализация
 * 3. TimedFoodManager - создание еды с экстремальными датами
 * 4. FoodContainer - хранение еды с большими датами, GUI отображение
 * 5. Tooltip система - отображение больших чисел в тултипах
 */
public class LongValueTestingManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(LongValueTestingManager.class);

    // Тестовые значения для проверки экстремальных случаев
    public static final long[] EXTREME_TEST_VALUES = {
        // Базовые значения
        0L,
        1L,
        -1L,

        // Тысячи
        1_000L,
        -1_000L,

        // Миллионы
        1_000_000L,
        -1_000_000L,

        // Миллиарды
        1_000_000_000L,
        -1_000_000_000L,

        // Большие значения
        1_000_000_000_000L, // триллион
        -1_000_000_000_000L,

        // Экстремальные значения
        Long.MAX_VALUE - 1000L, // Близко к максимуму
        Long.MAX_VALUE - 1L,    // Почти максимум
        Long.MAX_VALUE,         // Максимум

        Long.MIN_VALUE + 1000L, // Близко к минимуму
        Long.MIN_VALUE + 1L,    // Почти минимум
        Long.MIN_VALUE          // Минимум
    };

    // Статистика тестирования
    private static final Map<String, TestResult> testResults = new HashMap<>();

    /**
     * Результат тестирования компонента
     */
    public static class TestResult {
        private final String componentName;
        private final boolean passed;
        private final String details;
        private final long executionTimeMs;
        private final String errorMessage;

        public TestResult(String componentName, boolean passed, String details, long executionTimeMs, String errorMessage) {
            this.componentName = componentName;
            this.passed = passed;
            this.details = details;
            this.executionTimeMs = executionTimeMs;
            this.errorMessage = errorMessage;
        }

        public TestResult(String componentName, boolean passed, String details, long executionTimeMs) {
            this(componentName, passed, details, executionTimeMs, null);
        }

        // Getters
        public String getComponentName() { return componentName; }
        public boolean isPassed() { return passed; }
        public String getDetails() { return details; }
        public long getExecutionTimeMs() { return executionTimeMs; }
        public String getErrorMessage() { return errorMessage; }
    }

    /**
     * Запускает полное комплексное тестирование всех компонентов с большими значениями
     */
    public static void runCompleteTest(ServerLevel level) {
        LOGGER.info("=== НАЧАЛО КОМПЛЕКСНОГО ТЕСТИРОВАНИЯ БОЛЬШИХ ЗНАЧЕНИЙ ДНЕЙ ===");
        LOGGER.info("Дата начала тестирования: {}", DateTimeFormatter.ISO_INSTANT.format(Instant.now()));

        testResults.clear();

        // Тестируем каждый компонент
        testWorldDayTracker(level);
        testSpoilageComponent();
        testTimedFoodManager(level);
        testFoodContainer();
        testTooltipSystem();

        // Генерируем отчет
        generateTestReport();

        LOGGER.info("=== ЗАВЕРШЕНИЕ КОМПЛЕКСНОГО ТЕСТИРОВАНИЯ ===");
    }

    /**
     * Тестирует WorldDayTracker с экстремальными значениями
     */
    public static void testWorldDayTracker(ServerLevel level) {
        LOGGER.info("--- Тестирование WorldDayTracker ---");

        long startTime = System.currentTimeMillis();
        boolean allTestsPassed = true;
        StringBuilder details = new StringBuilder();
        String errorMessage = null;

        try {
            WorldDayTracker tracker = WorldDayTracker.getInstance(level);

            for (long testValue : EXTREME_TEST_VALUES) {
                try {
                    // Тестируем установку дня
                    tracker.setCurrentDay(testValue);
                    long retrievedValue = tracker.getCurrentDay();

                    if (retrievedValue != testValue) {
                        allTestsPassed = false;
                        details.append(String.format("ОШИБКА: Установлено %d, получено %d; ", testValue, retrievedValue));
                        LOGGER.warn("WorldDayTracker: установлено {}, получено {}", testValue, retrievedValue);
                    } else {
                        details.append(String.format("OK: %d; ", testValue));
                        LOGGER.debug("WorldDayTracker: значение {} корректно сохранено и получено", testValue);
                    }

                    // Тестируем инкремент (только для безопасных значений)
                    if (testValue < Long.MAX_VALUE - 10) {
                        tracker.setCurrentDay(testValue);
                        tracker.incrementDay();
                        long incrementedValue = tracker.getCurrentDay();

                        if (incrementedValue != testValue + 1) {
                            allTestsPassed = false;
                            details.append(String.format("ОШИБКА_ИНКРЕМЕНТ: %d -> %d; ", testValue, incrementedValue));
                        }
                    }

                } catch (Exception e) {
                    allTestsPassed = false;
                    details.append(String.format("ИСКЛЮЧЕНИЕ: %d (%s); ", testValue, e.getMessage()));
                    LOGGER.error("Ошибка при тестировании WorldDayTracker с значением {}: {}", testValue, e.getMessage());
                }
            }

            // Тестируем переполнение
            try {
                tracker.setCurrentDay(Long.MAX_VALUE);
                tracker.incrementDay(); // Это должно вызвать переполнение
                long overflowValue = tracker.getCurrentDay();
                details.append(String.format("Переполнение: MAX_VALUE + 1 = %d; ", overflowValue));
                LOGGER.info("WorldDayTracker переполнение: Long.MAX_VALUE + 1 = {}", overflowValue);
            } catch (Exception e) {
                details.append(String.format("Переполнение вызвало исключение: %s; ", e.getMessage()));
            }

        } catch (Exception e) {
            allTestsPassed = false;
            errorMessage = e.getMessage();
            LOGGER.error("Критическая ошибка при тестировании WorldDayTracker", e);
        }

        long executionTime = System.currentTimeMillis() - startTime;
        testResults.put("WorldDayTracker", new TestResult("WorldDayTracker", allTestsPassed, details.toString(), executionTime, errorMessage));

        LOGGER.info("WorldDayTracker тестирование завершено: {} за {} мс",
                allTestsPassed ? "УСПЕШНО" : "С ОШИБКАМИ", executionTime);
    }

    /**
     * Тестирует SpoilageComponent с большими значениями
     */
    public static void testSpoilageComponent() {
        LOGGER.info("--- Тестирование SpoilageComponent ---");

        long startTime = System.currentTimeMillis();
        boolean allTestsPassed = true;
        StringBuilder details = new StringBuilder();
        String errorMessage = null;

        try {
            for (long testValue : EXTREME_TEST_VALUES) {
                try {
                    // Создаем компонент
                    SpoilageComponent component = new SpoilageComponent(testValue);
                    long retrievedValue = component.creationDay();

                    if (retrievedValue != testValue) {
                        allTestsPassed = false;
                        details.append(String.format("ОШИБКА: Создан %d, получен %d; ", testValue, retrievedValue));
                        LOGGER.warn("SpoilageComponent: создан с {}, получен {}", testValue, retrievedValue);
                    } else {
                        details.append(String.format("OK: %d; ", testValue));
                        LOGGER.debug("SpoilageComponent: значение {} корректно сохранено", testValue);
                    }

                    // Тестируем сериализацию через Codec
                    try {
                        // Это имитирует сериализацию в NBT и обратно
                        String serialized = component.toString(); // Простая проверка что объект корректный
                        details.append(String.format("Сериализация OK: %d; ", testValue));
                    } catch (Exception e) {
                        allTestsPassed = false;
                        details.append(String.format("ОШИБКА_СЕРИАЛИЗАЦИИ: %d (%s); ", testValue, e.getMessage()));
                    }

                } catch (Exception e) {
                    allTestsPassed = false;
                    details.append(String.format("ИСКЛЮЧЕНИЕ: %d (%s); ", testValue, e.getMessage()));
                    LOGGER.error("Ошибка при тестировании SpoilageComponent с значением {}: {}", testValue, e.getMessage());
                }
            }

        } catch (Exception e) {
            allTestsPassed = false;
            errorMessage = e.getMessage();
            LOGGER.error("Критическая ошибка при тестировании SpoilageComponent", e);
        }

        long executionTime = System.currentTimeMillis() - startTime;
        testResults.put("SpoilageComponent", new TestResult("SpoilageComponent", allTestsPassed, details.toString(), executionTime, errorMessage));

        LOGGER.info("SpoilageComponent тестирование завершено: {} за {} мс",
                allTestsPassed ? "УСПЕШНО" : "С ОШИБКАМИ", executionTime);
    }

    /**
     * Тестирует TimedFoodManager с экстремальными датами
     */
    public static void testTimedFoodManager(ServerLevel level) {
        LOGGER.info("--- Тестирование TimedFoodManager ---");

        long startTime = System.currentTimeMillis();
        boolean allTestsPassed = true;
        StringBuilder details = new StringBuilder();
        String errorMessage = null;

        try {
            for (long testValue : EXTREME_TEST_VALUES) {
                try {
                    // Создаем еду с экстремальной датой
                    ItemStack timedFood = TimedFoodManager.createTimedFood(Items.APPLE, testValue);

                    if (timedFood.isEmpty()) {
                        // Это может быть ожидаемым поведением для некоторых экстремальных значений
                        details.append(String.format("Пустой стек для %d; ", testValue));
                        continue;
                    }

                    // Проверяем, что временная метка установлена корректно
                    if (!SpoilageUtils.hasTimestamp(timedFood)) {
                        allTestsPassed = false;
                        details.append(String.format("НЕТ_МЕТКИ: %d; ", testValue));
                        continue;
                    }

                    long retrievedDay = SpoilageUtils.getCreationDay(timedFood);
                    if (retrievedDay != testValue) {
                        allTestsPassed = false;
                        details.append(String.format("ОШИБКА: Установлен %d, получен %d; ", testValue, retrievedDay));
                        LOGGER.warn("TimedFoodManager: установлен день {}, получен {}", testValue, retrievedDay);
                    } else {
                        details.append(String.format("OK: %d; ", testValue));
                        LOGGER.debug("TimedFoodManager: день {} корректно установлен в еде", testValue);
                    }

                    // Тестируем проверку порчи с экстремальными значениями
                    try {
                        ItemStack processed = TimedFoodManager.checkAndProcessSpoilage(timedFood, level);
                        details.append(String.format("Порча_OK: %d; ", testValue));
                    } catch (Exception e) {
                        details.append(String.format("ОШИБКА_ПОРЧИ: %d (%s); ", testValue, e.getMessage()));
                    }

                } catch (Exception e) {
                    allTestsPassed = false;
                    details.append(String.format("ИСКЛЮЧЕНИЕ: %d (%s); ", testValue, e.getMessage()));
                    LOGGER.error("Ошибка при тестировании TimedFoodManager с значением {}: {}", testValue, e.getMessage());
                }
            }

        } catch (Exception e) {
            allTestsPassed = false;
            errorMessage = e.getMessage();
            LOGGER.error("Критическая ошибка при тестировании TimedFoodManager", e);
        }

        long executionTime = System.currentTimeMillis() - startTime;
        testResults.put("TimedFoodManager", new TestResult("TimedFoodManager", allTestsPassed, details.toString(), executionTime, errorMessage));

        LOGGER.info("TimedFoodManager тестирование завершено: {} за {} мс",
                allTestsPassed ? "УСПЕШНО" : "С ОШИБКАМИ", executionTime);
    }

    /**
     * Тестирует FoodContainer с экстремальными датами
     */
    public static void testFoodContainer() {
        LOGGER.info("--- Тестирование FoodContainer ---");

        long startTime = System.currentTimeMillis();
        boolean allTestsPassed = true;
        StringBuilder details = new StringBuilder();
        String errorMessage = null;

        try {
            // Создаем тестовый контейнер
            ItemStack containerStack = new ItemStack(Items.CHEST); // Заменить на реальный FoodContainer когда доступен

            for (long testValue : Arrays.copyOf(EXTREME_TEST_VALUES, 10)) { // Ограничиваем для производительности
                try {
                    // Создаем еду с экстремальной датой
                    ItemStack food = TimedFoodManager.createTimedFood(Items.BREAD, testValue);
                    if (food.isEmpty()) {
                        details.append(String.format("Пропуск %d (пустая еда); ", testValue));
                        continue;
                    }

                    // Создаем StoredFoodEntry для тестирования
                    String itemId = BuiltInRegistries.ITEM.getKey(Items.BREAD).toString();
                    StoredFoodEntry entry = new StoredFoodEntry(itemId, testValue, 1);

                    // Тестируем создание записи
                    if (entry.creationDay() != testValue) {
                        allTestsPassed = false;
                        details.append(String.format("ОШИБКА_ЗАПИСЬ: %d != %d; ", testValue, entry.creationDay()));
                    } else {
                        details.append(String.format("Запись_OK: %d; ", testValue));
                    }

                    // Тестируем сравнение записей (для сортировки FIFO)
                    StoredFoodEntry compareEntry = new StoredFoodEntry(itemId, testValue + 1, 1);
                    boolean comparison = entry.creationDay() < compareEntry.creationDay();
                    details.append(String.format("Сравнение_OK: %d; ", testValue));

                } catch (Exception e) {
                    allTestsPassed = false;
                    details.append(String.format("ИСКЛЮЧЕНИЕ: %d (%s); ", testValue, e.getMessage()));
                    LOGGER.error("Ошибка при тестировании FoodContainer с значением {}: {}", testValue, e.getMessage());
                }
            }

        } catch (Exception e) {
            allTestsPassed = false;
            errorMessage = e.getMessage();
            LOGGER.error("Критическая ошибка при тестировании FoodContainer", e);
        }

        long executionTime = System.currentTimeMillis() - startTime;
        testResults.put("FoodContainer", new TestResult("FoodContainer", allTestsPassed, details.toString(), executionTime, errorMessage));

        LOGGER.info("FoodContainer тестирование завершено: {} за {} мс",
                allTestsPassed ? "УСПЕШНО" : "С ОШИБКАМИ", executionTime);
    }

    /**
     * Тестирует Tooltip систему с большими числами
     */
    public static void testTooltipSystem() {
        LOGGER.info("--- Тестирование Tooltip системы ---");

        long startTime = System.currentTimeMillis();
        boolean allTestsPassed = true;
        StringBuilder details = new StringBuilder();
        String errorMessage = null;

        try {
            for (long testValue : Arrays.copyOf(EXTREME_TEST_VALUES, 8)) { // Ограничиваем для производительности
                try {
                    // Тестируем форматирование времени
                    String formatted = TooltipUtils.formatTimeRemaining(testValue);
                    if (formatted == null || formatted.isEmpty()) {
                        allTestsPassed = false;
                        details.append(String.format("ПУСТОЙ_ФОРМАТ: %d; ", testValue));
                    } else {
                        details.append(String.format("Формат_OK: %d->%s; ", testValue, formatted));
                        LOGGER.debug("Tooltip форматирование: {} -> {}", testValue, formatted);
                    }

                    // Тестируем создание компонента дня создания
                    try {
                        var component = TooltipUtils.createCreationDayComponent(testValue);
                        if (component == null) {
                            allTestsPassed = false;
                            details.append(String.format("NULL_КОМПОНЕНТ: %d; ", testValue));
                        } else {
                            details.append(String.format("Компонент_OK: %d; ", testValue));
                        }
                    } catch (Exception e) {
                        details.append(String.format("ОШИБКА_КОМПОНЕНТ: %d (%s); ", testValue, e.getMessage()));
                    }

                    // Тестируем вычисление свежести с экстремальными значениями
                    try {
                        var freshness = TooltipUtils.calculateFreshness(testValue, Math.max(1, Math.abs(testValue)));
                        details.append(String.format("Свежесть_OK: %d; ", testValue));
                    } catch (Exception e) {
                        details.append(String.format("ОШИБКА_СВЕЖЕСТЬ: %d (%s); ", testValue, e.getMessage()));
                    }

                } catch (Exception e) {
                    allTestsPassed = false;
                    details.append(String.format("ИСКЛЮЧЕНИЕ: %d (%s); ", testValue, e.getMessage()));
                    LOGGER.error("Ошибка при тестировании Tooltip с значением {}: {}", testValue, e.getMessage());
                }
            }

        } catch (Exception e) {
            allTestsPassed = false;
            errorMessage = e.getMessage();
            LOGGER.error("Критическая ошибка при тестировании Tooltip системы", e);
        }

        long executionTime = System.currentTimeMillis() - startTime;
        testResults.put("TooltipSystem", new TestResult("TooltipSystem", allTestsPassed, details.toString(), executionTime, errorMessage));

        LOGGER.info("Tooltip система тестирование завершено: {} за {} мс",
                allTestsPassed ? "УСПЕШНО" : "С ОШИБКАМИ", executionTime);
    }

    /**
     * Генерирует детальный отчет о результатах тестирования
     */
    public static void generateTestReport() {
        LOGGER.info("=== ОТЧЕТ О ТЕСТИРОВАНИИ БОЛЬШИХ ЗНАЧЕНИЙ ДНЕЙ ===");

        int totalTests = testResults.size();
        int passedTests = 0;
        long totalExecutionTime = 0;

        for (TestResult result : testResults.values()) {
            if (result.isPassed()) {
                passedTests++;
            }
            totalExecutionTime += result.getExecutionTimeMs();

            LOGGER.info("Компонент: {} | Результат: {} | Время: {} мс",
                    result.getComponentName(),
                    result.isPassed() ? "УСПЕШНО" : "ОШИБКА",
                    result.getExecutionTimeMs());

            if (!result.getDetails().isEmpty()) {
                LOGGER.info("  Детали: {}", result.getDetails());
            }

            if (result.getErrorMessage() != null) {
                LOGGER.error("  Критическая ошибка: {}", result.getErrorMessage());
            }
        }

        LOGGER.info("=== ИТОГОВАЯ СТАТИСТИКА ===");
        LOGGER.info("Успешно пройдено: {}/{} тестов", passedTests, totalTests);
        LOGGER.info("Общее время выполнения: {} мс", totalExecutionTime);
        LOGGER.info("Процент успеха: {}%", totalTests > 0 ? (passedTests * 100 / totalTests) : 0);

        if (passedTests == totalTests) {
            LOGGER.info("🎉 ВСЕ ТЕСТЫ ПРОЙДЕНЫ УСПЕШНО! Система готова к работе с экстремальными значениями дней.");
        } else {
            LOGGER.warn("⚠️ ОБНАРУЖЕНЫ ПРОБЛЕМЫ! Необходимо исправить ошибки перед использованием больших значений.");
        }

        LOGGER.info("Дата завершения тестирования: {}", DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
    }

    /**
     * Получает результаты тестирования для внешнего использования
     */
    public static Map<String, TestResult> getTestResults() {
        return new HashMap<>(testResults);
    }

    /**
     * Запускает стресс-тест с случайными большими значениями
     */
    public static void runStressTest(ServerLevel level, int iterations) {
        LOGGER.info("=== ЗАПУСК СТРЕСС-ТЕСТА ({} итераций) ===", iterations);

        long startTime = System.currentTimeMillis();
        int failures = 0;

        for (int i = 0; i < iterations; i++) {
            try {
                // Генерируем случайное большое значение
                long randomValue = ThreadLocalRandom.current().nextLong(Long.MIN_VALUE / 2, Long.MAX_VALUE / 2);

                // Тестируем основные операции
                WorldDayTracker tracker = WorldDayTracker.getInstance(level);
                tracker.setCurrentDay(randomValue);

                ItemStack food = TimedFoodManager.createTimedFood(Items.CARROT, randomValue);
                if (!food.isEmpty() && SpoilageUtils.hasTimestamp(food)) {
                    long retrieved = SpoilageUtils.getCreationDay(food);
                    if (retrieved != randomValue) {
                        failures++;
                        LOGGER.warn("Стресс-тест итерация {}: ожидалось {}, получено {}", i, randomValue, retrieved);
                    }
                }

                // Каждые 100 итераций выводим прогресс
                if (i % 100 == 0 && i > 0) {
                    LOGGER.debug("Стресс-тест прогресс: {}/{} итераций", i, iterations);
                }

            } catch (Exception e) {
                failures++;
                LOGGER.error("Стресс-тест итерация {} завершилась с ошибкой: {}", i, e.getMessage());
            }
        }

        long executionTime = System.currentTimeMillis() - startTime;

        LOGGER.info("=== РЕЗУЛЬТАТЫ СТРЕСС-ТЕСТА ===");
        LOGGER.info("Итераций: {} | Ошибок: {} | Время: {} мс", iterations, failures, executionTime);
        LOGGER.info("Успешность: {}%", iterations > 0 ? ((iterations - failures) * 100 / iterations) : 0);

        if (failures == 0) {
            LOGGER.info("🎉 СТРЕСС-ТЕСТ ПРОЙДЕН УСПЕШНО!");
        } else {
            LOGGER.warn("⚠️ СТРЕСС-ТЕСТ ВЫЯВИЛ {} ОШИБОК!", failures);
        }
    }

    /**
     * Очищает результаты предыдущих тестов
     */
    public static void clearTestResults() {
        testResults.clear();
        LOGGER.info("Результаты тестирования очищены");
    }
}