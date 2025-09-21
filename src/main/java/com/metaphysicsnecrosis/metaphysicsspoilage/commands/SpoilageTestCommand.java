package com.metaphysicsnecrosis.metaphysicsspoilage.commands;

import com.metaphysicsnecrosis.metaphysicsspoilage.items.FoodContainer;
import com.metaphysicsnecrosis.metaphysicsspoilage.spoilage.SpoilageUtils;
import com.metaphysicsnecrosis.metaphysicsspoilage.time.WorldDayTracker;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;

/**
 * Команда для тестирования системы порчи
 */
public class SpoilageTestCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpoilageTestCommand.class);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("spoilage_test")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("add_old_food")
                .then(Commands.argument("days_old", IntegerArgumentType.integer(1, 30))
                    .executes(SpoilageTestCommand::addOldFood)
                )
            )
            .then(Commands.literal("check_container")
                .executes(SpoilageTestCommand::checkContainer)
            )
            .then(Commands.literal("force_spoilage_check")
                .executes(SpoilageTestCommand::forceSpoilageCheck)
            )
        );
    }

    private static int addOldFood(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("Команда доступна только игрокам"));
            return 0;
        }

        int daysOld = IntegerArgumentType.getInteger(context, "days_old");
        ServerLevel level = source.getLevel();

        // Создаем старую еду (говядину)
        ItemStack beef = new ItemStack(Items.BEEF, 5);
        long currentDay = WorldDayTracker.getInstance(level).getCurrentDay();
        long creationDay = currentDay - daysOld;

        // Устанавливаем день создания
        SpoilageUtils.setCreationDay(beef, creationDay);

        // Добавляем в инвентарь
        if (player.getInventory().add(beef)) {
            source.sendSuccess(() -> Component.literal(
                String.format("Добавлена говядина возрастом %d дней (создана в день %d, текущий день %d)",
                    daysOld, creationDay, currentDay)
            ), false);

            LOGGER.info("Игроку {} добавлена говядина возрастом {} дней",
                player.getName().getString(), daysOld);
        } else {
            source.sendFailure(Component.literal("Инвентарь полон"));
        }

        return 1;
    }

    private static int checkContainer(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("Команда доступна только игрокам"));
            return 0;
        }

        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.isEmpty() || !(mainHand.getItem() instanceof FoodContainer)) {
            source.sendFailure(Component.literal("Держите контейнер с едой в основной руке"));
            return 0;
        }

        ServerLevel level = source.getLevel();

        source.sendSuccess(() -> Component.literal("=== ПРОВЕРКА КОНТЕЙНЕРА ==="), false);

        // Получаем и выводим содержимое
        var storedFoods = FoodContainer.getStoredFoods(mainHand);
        source.sendSuccess(() -> Component.literal(
            String.format("Контейнер содержит %d типов еды:", storedFoods.size())
        ), false);

        long currentDay = WorldDayTracker.getInstance(level).getCurrentDay();

        for (var entry : storedFoods) {
            long daysDiff = currentDay - entry.creationDay();

            // Создаем временный ItemStack для проверки
            Item item = BuiltInRegistries.ITEM.getValue(ResourceLocation.parse(entry.itemId()));
            if (item != null) {
                ItemStack tempStack = new ItemStack(item, entry.count());
                SpoilageUtils.setCreationDay(tempStack, entry.creationDay());

                boolean isSpoiled = com.metaphysicsnecrosis.metaphysicsspoilage.spoilage.SpoilageChecker.isItemSpoiled(tempStack, level);

                source.sendSuccess(() -> Component.literal(String.format(
                    "  - %s x%d (день %d, прошло %d дней) - %s",
                    entry.itemId(), entry.count(), entry.creationDay(), daysDiff,
                    isSpoiled ? "ИСПОРЧЕНА" : "СВЕЖАЯ"
                )), false);
            }
        }

        // Принудительно проверяем порчу
        LOGGER.info("=== ПРИНУДИТЕЛЬНАЯ ПРОВЕРКА ПОРЧИ ===");
        FoodContainer.checkAndRemoveSpoiledFood(mainHand, level);

        source.sendSuccess(() -> Component.literal("Проверка завершена. Смотрите логи для деталей."), false);

        return 1;
    }

    private static int forceSpoilageCheck(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Player player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.literal("Команда доступна только игрокам"));
            return 0;
        }

        ServerLevel level = source.getLevel();

        // Принудительно проверяем инвентарь игрока
        LOGGER.info("=== ПРИНУДИТЕЛЬНАЯ ПРОВЕРКА ИНВЕНТАРЯ ===");
        com.metaphysicsnecrosis.metaphysicsspoilage.spoilage.SpoilageChecker.checkPlayerInventory(player, level);

        source.sendSuccess(() -> Component.literal("Принудительная проверка инвентаря выполнена. Смотрите логи."), false);

        return 1;
    }
}