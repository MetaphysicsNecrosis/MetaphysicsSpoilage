/**
 * Пакет для тестирования системы порчи еды с большими значениями дней.
 *
 * Содержит инструменты для комплексного тестирования всех компонентов мода
 * на работоспособность с экстремальными значениями long:
 * - Long.MAX_VALUE (9,223,372,036,854,775,807)
 * - Long.MIN_VALUE (-9,223,372,036,854,775,808)
 * - Миллионы и миллиарды дней
 * - Отрицательные значения
 * - Переполнение и underflow сценарии
 *
 * Основные классы:
 * - {@link com.metaphysicsnecrosis.metaphysicsspoilage.testing.LongValueTestingManager} - основной менеджер тестирования
 *
 * Тестируемые компоненты:
 * - WorldDayTracker - сохранение/загрузка больших значений дней
 * - SpoilageComponent - DataComponent с long значениями, Codec сериализация
 * - TimedFoodManager - создание еды с экстремальными датами
 * - FoodContainer - хранение еды с большими датами, GUI отображение
 * - Tooltip система - отображение больших чисел в тултипах
 *
 * @author MetaphysicsNecrosis
 * @version 1.0
 * @since 1.21.8
 */
package com.metaphysicsnecrosis.metaphysicsspoilage.testing;