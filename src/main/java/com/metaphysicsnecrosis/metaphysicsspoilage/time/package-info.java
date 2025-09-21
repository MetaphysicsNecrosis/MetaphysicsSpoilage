/**
 * Пакет для работы с игровым временем и отслеживанием дней в MetaphysicsSpoilage.
 *
 * <h2>Основные классы:</h2>
 * <ul>
 *   <li>{@link com.metaphysicsnecrosis.metaphysicsspoilage.time.WorldDayTracker} - Основной класс для отслеживания игровых дней с использованием SavedData</li>
 *   <li>{@link com.metaphysicsnecrosis.metaphysicsspoilage.time.TimeUtils} - Утилитный класс для работы с игровым временем</li>
 *   <li>{@link com.metaphysicsnecrosis.metaphysicsspoilage.time.DayTrackingExample} - Примеры использования системы</li>
 * </ul>
 *
 * <h2>Основные возможности:</h2>
 * <ul>
 *   <li>Отслеживание неограниченного количества игровых дней (long)</li>
 *   <li>Автоматическая синхронизация с мировым временем</li>
 *   <li>Сохранение данных между сессиями игры</li>
 *   <li>Утилиты для работы с временем суток</li>
 *   <li>Поддержка различных измерений (dimensions)</li>
 * </ul>
 *
 * <h2>Пример использования:</h2>
 * <pre>{@code
 * // Получить трекер дней для уровня
 * WorldDayTracker tracker = WorldDayTracker.getInstance(serverLevel);
 *
 * // Получить текущий день
 * long currentDay = tracker.getCurrentDay();
 *
 * // Проверить, день ли сейчас
 * boolean isDaytime = TimeUtils.isDaytime(level);
 *
 * // Синхронизировать с мировым временем
 * TimeUtils.synchronizeDayTracker(serverLevel);
 * }</pre>
 *
 * @author MetaphysicsNecrosis
 * @version 1.0
 * @since 1.21.8
 */
package com.metaphysicsnecrosis.metaphysicsspoilage.time;