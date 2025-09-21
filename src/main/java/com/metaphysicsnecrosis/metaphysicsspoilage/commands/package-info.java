/**
 * Пакет для команд системы порчи еды.
 *
 * Содержит команды для управления и тестирования системы порчи:
 * - Команды тестирования больших значений дней
 * - Команды управления временем мира
 * - Команды создания еды с временными метками
 * - Команды диагностики и отладки
 *
 * Основные классы:
 * - {@link com.metaphysicsnecrosis.metaphysicsspoilage.commands.LongValueTestCommand} - команды тестирования
 *
 * Доступные команды:
 * - /spoilage_test run - запуск полного комплексного тестирования
 * - /spoilage_test stress <iterations> - запуск стресс-теста
 * - /spoilage_test create <item> <day> - создание еды с определенным днем
 * - /spoilage_test setday <day> - установка текущего дня мира
 * - /spoilage_test getday - получение текущего дня мира
 * - /spoilage_test extreme - тестирование с экстремальными значениями
 * - /spoilage_test clear - очистка результатов тестов
 * - /spoilage_test report - показ краткого отчета
 *
 * @author MetaphysicsNecrosis
 * @version 1.0
 * @since 1.21.8
 */
package com.metaphysicsnecrosis.metaphysicsspoilage.commands;