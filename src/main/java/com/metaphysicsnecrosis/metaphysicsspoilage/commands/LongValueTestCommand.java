package com.metaphysicsnecrosis.metaphysicsspoilage.commands;

import com.metaphysicsnecrosis.metaphysicsspoilage.manager.TimedFoodManager;
import com.metaphysicsnecrosis.metaphysicsspoilage.spoilage.SpoilageUtils;
import com.metaphysicsnecrosis.metaphysicsspoilage.testing.LongValueTestingManager;
import com.metaphysicsnecrosis.metaphysicsspoilage.time.TimeUtils;
import com.metaphysicsnecrosis.metaphysicsspoilage.time.WorldDayTracker;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Команды для тестирования системы порчи с большими значениями дней.
 *
 * Доступные команды:
 * - /spoilage_test run - запуск полного комплексного тестирования
 * - /spoilage_test stress <iterations> - запуск стресс-теста
 * - /spoilage_test create <item> <day> - создание еды с определенным днем
 * - /spoilage_test setday <day> - установка текущего дня мира
 * - /spoilage_test getday - получение текущего дня мира
 * - /spoilage_test extreme - тестирование с экстремальными значениями
 */
public class LongValueTestCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(LongValueTestCommand.class);

    /**
     * Регистрирует команды тестирования
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(Commands.literal("spoilage_test")
            .requires(source -> source.hasPermission(2)) // Только для OP
            .then(Commands.literal("run")
                .executes(LongValueTestCommand::runCompleteTest))
            .then(Commands.literal("stress")
                .then(Commands.argument("iterations", IntegerArgumentType.integer(1, 10000))
                    .executes(LongValueTestCommand::runStressTest)))
            .then(Commands.literal("create")
                .then(Commands.argument("item", ItemArgument.item(context))
                    .then(Commands.argument("day", LongArgumentType.longArg())
                        .executes(LongValueTestCommand::createTimedFood))))
            .then(Commands.literal("setday")
                .then(Commands.argument("day", LongArgumentType.longArg())
                    .executes(LongValueTestCommand::setWorldDay)))
            .then(Commands.literal("getday")
                .executes(LongValueTestCommand::getWorldDay))
            .then(Commands.literal("extreme")
                .executes(LongValueTestCommand::testExtremeValues))
            .then(Commands.literal("clear")
                .executes(LongValueTestCommand::clearTestResults))
            .then(Commands.literal("report")
                .executes(LongValueTestCommand::showTestReport))
        );

        LOGGER.info("Команды тестирования больших значений зарегистрированы");
    }

    /**
     * Запускает полное комплексное тестирование
     */
    private static int runCompleteTest(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();

        source.sendSuccess(() -> Component.literal("Запуск комплексного тестирования больших значений дней...")
            .withStyle(ChatFormatting.YELLOW), false);

        try {
            // Запускаем тестирование в отдельном потоке чтобы не блокировать сервер
            new Thread(() -> {
                try {
                    LongValueTestingManager.runCompleteTest(level);

                    source.sendSuccess(() -> Component.literal("✅ Комплексное тестирование завершено! Проверьте логи для подробностей.")
                        .withStyle(ChatFormatting.GREEN), false);
                } catch (Exception e) {
                    LOGGER.error("Ошибка при выполнении комплексного тестирования", e);
                    source.sendFailure(Component.literal("❌ Ошибка при тестировании: " + e.getMessage())
                        .withStyle(ChatFormatting.RED));
                }
            }).start();

            return 1;
        } catch (Exception e) {
            LOGGER.error("Ошибка при запуске комплексного тестирования", e);
            source.sendFailure(Component.literal("❌ Ошибка при запуске тестирования: " + e.getMessage())
                .withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    /**
     * Запускает стресс-тест с указанным количеством итераций
     */
    private static int runStressTest(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        int iterations = IntegerArgumentType.getInteger(context, "iterations");

        source.sendSuccess(() -> Component.literal("Запуск стресс-теста с " + iterations + " итерациями...")
            .withStyle(ChatFormatting.YELLOW), false);

        try {
            // Запускаем стресс-тест в отдельном потоке
            new Thread(() -> {
                try {
                    LongValueTestingManager.runStressTest(level, iterations);

                    source.sendSuccess(() -> Component.literal("✅ Стресс-тест завершен! Проверьте логи для результатов.")
                        .withStyle(ChatFormatting.GREEN), false);
                } catch (Exception e) {
                    LOGGER.error("Ошибка при выполнении стресс-теста", e);
                    source.sendFailure(Component.literal("❌ Ошибка при стресс-тесте: " + e.getMessage())
                        .withStyle(ChatFormatting.RED));
                }
            }).start();

            return 1;
        } catch (Exception e) {
            LOGGER.error("Ошибка при запуске стресс-теста", e);
            source.sendFailure(Component.literal("❌ Ошибка при запуске стресс-теста: " + e.getMessage())
                .withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    /**
     * Создает еду с указанным днем создания
     */
    private static int createTimedFood(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        ItemInput itemInput = ItemArgument.getItem(context, "item");
        long day = LongArgumentType.getLong(context, "day");

        try {
            ServerPlayer player = source.getPlayerOrException();
            ItemStack timedFood = TimedFoodManager.createTimedFood(itemInput.getItem(), day);

            if (timedFood.isEmpty()) {
                source.sendFailure(Component.literal("❌ Не удалось создать еду с днем " + day +
                    ". Возможно, предмет не является едой или день недопустим.")
                    .withStyle(ChatFormatting.RED));
                return 0;
            }

            // Даем предмет игроку
            if (!player.getInventory().add(timedFood)) {
                // Если инвентарь полон, дропаем предмет
                player.drop(timedFood, false);
            }

            String itemName = BuiltInRegistries.ITEM.getKey(itemInput.getItem()).toString();
            source.sendSuccess(() -> Component.literal("✅ Создан предмет " + itemName + " с днем создания: " + day)
                .withStyle(ChatFormatting.GREEN), false);

            // Дополнительная информация о сроке годности
            long currentDay = WorldDayTracker.getInstance(level).getCurrentDay();
            long daysUntilSpoilage = TimedFoodManager.getDaysUntilSpoilage(timedFood, level);

            if (daysUntilSpoilage != -1) {
                source.sendSuccess(() -> Component.literal("📅 Текущий день: " + currentDay +
                    ", дней до порчи: " + daysUntilSpoilage)
                    .withStyle(ChatFormatting.GRAY), false);
            }

            return 1;
        } catch (Exception e) {
            LOGGER.error("Ошибка при создании еды с днем {}", day, e);
            source.sendFailure(Component.literal("❌ Ошибка при создании еды: " + e.getMessage())
                .withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    /**
     * Устанавливает текущий день мира
     */
    private static int setWorldDay(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        long day = LongArgumentType.getLong(context, "day");

        try {
            long oldDay = WorldDayTracker.getInstance(level).getCurrentDay();
            TimeUtils.setDay(level, day);

            source.sendSuccess(() -> Component.literal("✅ День мира изменен с " + oldDay + " на " + day)
                .withStyle(ChatFormatting.GREEN), false);

            // Предупреждение о больших значениях
            if (Math.abs(day) > 1_000_000_000L) {
                source.sendSuccess(() -> Component.literal("⚠️ Внимание: установлено экстремально большое значение дня!")
                    .withStyle(ChatFormatting.YELLOW), false);
            }

            return 1;
        } catch (Exception e) {
            LOGGER.error("Ошибка при установке дня {}", day, e);
            source.sendFailure(Component.literal("❌ Ошибка при установке дня: " + e.getMessage())
                .withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    /**
     * Получает текущий день мира
     */
    private static int getWorldDay(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();

        try {
            WorldDayTracker tracker = WorldDayTracker.getInstance(level);
            long currentDay = tracker.getCurrentDay();
            long worldDay = TimeUtils.getCurrentDayFromWorldTime(level);

            source.sendSuccess(() -> Component.literal("📅 Информация о дне:")
                .withStyle(ChatFormatting.AQUA), false);
            source.sendSuccess(() -> Component.literal("  WorldDayTracker: " + currentDay)
                .withStyle(ChatFormatting.WHITE), false);
            source.sendSuccess(() -> Component.literal("  Мировое время: " + worldDay + " день")
                .withStyle(ChatFormatting.WHITE), false);

            if (currentDay != worldDay) {
                source.sendSuccess(() -> Component.literal("  ⚠️ Обнаружено рассинхронизация времени!")
                    .withStyle(ChatFormatting.YELLOW), false);
            }

            return 1;
        } catch (Exception e) {
            LOGGER.error("Ошибка при получении дня", e);
            source.sendFailure(Component.literal("❌ Ошибка при получении дня: " + e.getMessage())
                .withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    /**
     * Тестирует экстремальные значения
     */
    private static int testExtremeValues(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();

        source.sendSuccess(() -> Component.literal("🧪 Тестирование экстремальных значений...")
            .withStyle(ChatFormatting.YELLOW), false);

        try {
            ServerPlayer player = source.getPlayerOrException();

            // Тестируем несколько экстремальных значений
            long[] testValues = {
                Long.MAX_VALUE,
                Long.MIN_VALUE,
                1_000_000_000L,
                -1_000_000_000L,
                0L
            };

            int successCount = 0;
            for (long testValue : testValues) {
                try {
                    // Тестируем WorldDayTracker
                    WorldDayTracker tracker = WorldDayTracker.getInstance(level);
                    tracker.setCurrentDay(testValue);
                    long retrieved = tracker.getCurrentDay();

                    boolean success = (retrieved == testValue);
                    if (success) {
                        successCount++;
                    }

                    // Тестируем создание еды
                    ItemStack food = TimedFoodManager.createTimedFood(Items.APPLE, testValue);
                    boolean foodSuccess = !food.isEmpty() && SpoilageUtils.hasTimestamp(food);

                    String status = (success && foodSuccess) ? "✅" : "❌";
                    source.sendSuccess(() -> Component.literal(status + " " + testValue +
                        " (Tracker: " + success + ", Food: " + foodSuccess + ")")
                        .withStyle(success && foodSuccess ? ChatFormatting.GREEN : ChatFormatting.RED), false);

                } catch (Exception e) {
                    source.sendSuccess(() -> Component.literal("❌ " + testValue + " - Ошибка: " + e.getMessage())
                        .withStyle(ChatFormatting.RED), false);
                }
            }

            final int finalSuccessCount = successCount;
            source.sendSuccess(() -> Component.literal("🏁 Тестирование завершено. Успешно: " +
                finalSuccessCount + "/" + testValues.length)
                .withStyle(ChatFormatting.AQUA), false);

            return 1;
        } catch (Exception e) {
            LOGGER.error("Ошибка при тестировании экстремальных значений", e);
            source.sendFailure(Component.literal("❌ Ошибка при тестировании: " + e.getMessage())
                .withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    /**
     * Очищает результаты предыдущих тестов
     */
    private static int clearTestResults(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();

        try {
            LongValueTestingManager.clearTestResults();
            source.sendSuccess(() -> Component.literal("✅ Результаты тестирования очищены")
                .withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (Exception e) {
            LOGGER.error("Ошибка при очистке результатов", e);
            source.sendFailure(Component.literal("❌ Ошибка при очистке: " + e.getMessage())
                .withStyle(ChatFormatting.RED));
            return 0;
        }
    }

    /**
     * Показывает краткий отчет о результатах тестирования
     */
    private static int showTestReport(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();

        try {
            var results = LongValueTestingManager.getTestResults();

            if (results.isEmpty()) {
                source.sendSuccess(() -> Component.literal("📋 Нет данных о тестировании. Запустите /spoilage_test run")
                    .withStyle(ChatFormatting.GRAY), false);
                return 1;
            }

            source.sendSuccess(() -> Component.literal("📋 Краткий отчет о тестировании:")
                .withStyle(ChatFormatting.AQUA), false);

            int passed = 0;
            int total = results.size();

            for (var result : results.values()) {
                if (result.isPassed()) {
                    passed++;
                }

                String status = result.isPassed() ? "✅" : "❌";
                source.sendSuccess(() -> Component.literal(status + " " + result.getComponentName() +
                    " (" + result.getExecutionTimeMs() + " мс)")
                    .withStyle(result.isPassed() ? ChatFormatting.GREEN : ChatFormatting.RED), false);
            }

            final int finalPassed = passed;
            final int finalTotal = total;
            source.sendSuccess(() -> Component.literal("🏁 Итого: " + finalPassed + "/" + finalTotal + " тестов пройдено")
                .withStyle(finalPassed == finalTotal ? ChatFormatting.GREEN : ChatFormatting.YELLOW), false);

            return 1;
        } catch (Exception e) {
            LOGGER.error("Ошибка при показе отчета", e);
            source.sendFailure(Component.literal("❌ Ошибка при показе отчета: " + e.getMessage())
                .withStyle(ChatFormatting.RED));
            return 0;
        }
    }
}