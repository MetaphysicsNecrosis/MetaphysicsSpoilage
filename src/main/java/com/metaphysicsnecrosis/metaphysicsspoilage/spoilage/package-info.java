/**
 * Пакет для системы порчи предметов.
 *
 * <p>Содержит классы для управления временными метками на ItemStack
 * и проверки порчи предметов на основе игрового времени.</p>
 *
 * <h3>Основные компоненты:</h3>
 * <ul>
 *   <li>{@link com.metaphysicsnecrosis.metaphysicsspoilage.spoilage.SpoilageUtils} -
 *       Утилитные методы для работы с компонентами порчи</li>
 *   <li>{@link com.metaphysicsnecrosis.metaphysicsspoilage.spoilage.SpoilageData} -
 *       Данные о настройках порчи для предметов</li>
 *   <li>{@link com.metaphysicsnecrosis.metaphysicsspoilage.spoilage.SpoilageEventHandler} -
 *       Обработчик событий для автоматической установки временных меток</li>
 * </ul>
 *
 * <h3>Использование:</h3>
 * <pre>{@code
 * // Проверить, может ли предмет портиться
 * boolean canSpoil = SpoilageUtils.canItemSpoil(Items.BREAD);
 *
 * // Установить временную метку на предмет
 * SpoilageUtils.setCreationDay(itemStack, currentDay);
 *
 * // Проверить, испортился ли предмет
 * boolean isSpoiled = SpoilageUtils.isItemSpoiled(itemStack, serverLevel);
 *
 * // Получить количество дней до порчи
 * long daysLeft = SpoilageUtils.getDaysUntilSpoilage(itemStack, serverLevel);
 * }</pre>
 */
package com.metaphysicsnecrosis.metaphysicsspoilage.spoilage;