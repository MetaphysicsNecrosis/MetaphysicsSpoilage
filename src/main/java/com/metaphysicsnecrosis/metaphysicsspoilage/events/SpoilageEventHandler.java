package com.metaphysicsnecrosis.metaphysicsspoilage.events;

import com.metaphysicsnecrosis.metaphysicsspoilage.Config;
import com.metaphysicsnecrosis.metaphysicsspoilage.manager.TimedFoodManager;
import com.metaphysicsnecrosis.metaphysicsspoilage.spoilage.SpoilageChecker;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Комплексный обработчик событий для автоматической проверки порчи предметов.
 *
 * Обрабатывает следующие события:
 * - Открытие контейнеров (проверка на порчу)
 * - Попытки употребления еды (проверка и блокировка)
 * - Периодические проверки инвентарей игроков
 * - Вход/выход игроков (инициализация/очистка)
 * - Серверные тики (фоновые задачи)
 *
 * @author MetaphysicsNecrosis
 * @version 1.0
 * @since 1.21.8
 */
@EventBusSubscriber
public class SpoilageEventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpoilageEventHandler.class);

    // === УПРАВЛЕНИЕ ПЕРИОДИЧЕСКИМИ ПРОВЕРКАМИ ===

    /**
     * Интервал между автоматическими проверками инвентарей в тиках (20 тиков = 1 секунда)
     * По умолчанию: каждые 30 секунд
     */
    private static final int INVENTORY_CHECK_INTERVAL = 20 * 5;

    /**
     * Счетчик тиков сервера для периодических задач
     */
    private static long serverTickCounter = 0;

    /**
     * Карта последних проверок инвентарей игроков (UUID -> номер тика)
     */
    private static final Map<UUID, Long> LAST_INVENTORY_CHECK = new ConcurrentHashMap<>();

    /**
     * Карта открытых контейнеров для предотвращения дублирующих проверок
     */
    private static final Map<UUID, Set<Integer>> OPEN_CONTAINERS = new ConcurrentHashMap<>();

    // === СОБЫТИЕ: УПОТРЕБЛЕНИЕ ПРЕДМЕТОВ ===

    /**
     * Обрабатывает событие начала употребления предметов.
     * Проверяет предметы на порчу и блокирует употребление испорченной еды.
     */
    @SubscribeEvent
    public static void onItemUseStart(LivingEntityUseItemEvent.Start event) {
        // Проверяем только игроков на серверной стороне
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        Level level = player.level();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        // Проверяем, включена ли система порчи
        if (!Config.ENABLE_SPOILAGE_SYSTEM.get()) {
            return;
        }

        ItemStack itemStack = event.getItem();
        if (itemStack.isEmpty()) {
            return;
        }

        // Проверяем, является ли предмет едой
        if (!itemStack.has(DataComponents.FOOD)) {
            return;
        }

        String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(itemStack.getItem()).toString();

        // Проверяем, есть ли предмет в списке всегда съедобных
        if (Config.isAlwaysEdible(itemId)) {
            LOGGER.debug("Разрешено употребление всегда съедобного предмета: {}", itemId);
            return;
        }

        // Проверяем наличие временной метки
        if (!TimedFoodManager.isTimedFood(itemStack)) {
            // Предмет без временной метки
            Config.FoodBlockingMode blockingMode = Config.FOOD_BLOCKING_MODE.get();

            switch (blockingMode) {
                case FULL_BLOCK:
                    // Полная блокировка употребления
                    event.setCanceled(true);
                    LOGGER.debug("Заблокировано употребление еды без временной метки: {}", itemId);
                    return;

                case ZERO_NUTRITION:
                    // Разрешаем употребление, но обнуляем питательность
                    // Это требует дополнительной обработки в другом событии
                    LOGGER.debug("Разрешено употребление без питательности: {}", itemId);
                    return;
            }
        }

        // Проверяем, испорчен ли предмет
        if (SpoilageChecker.isItemSpoiled(itemStack, serverLevel)) {
            event.setCanceled(true);

            // Автоматически обрабатываем порчу предмета в руке
            ItemStack processed = SpoilageChecker.processSpoilage(itemStack, serverLevel);
            if (!ItemStack.matches(itemStack, processed)) {
                // Заменяем предмет в руке на обработанный (испорченный или пустой)
                ItemStack selectedItem = player.getMainHandItem();
                if (ItemStack.matches(selectedItem, itemStack)) {
                    player.setItemInHand(InteractionHand.MAIN_HAND, processed);
                }
            }
        }
    }

    /**
     * Обрабатывает завершение употребления предметов для обнуления питательности
     * при употреблении еды без временной метки в режиме ZERO_NUTRITION.
     */
    @SubscribeEvent
    public static void onItemUseFinish(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (!Config.ENABLE_SPOILAGE_SYSTEM.get()) {
            return;
        }

        ItemStack itemStack = event.getItem();
        if (!itemStack.has(DataComponents.FOOD)) {
            return;
        }

        String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(itemStack.getItem()).toString();

        // Проверяем режим блокировки и наличие временной метки
        if (Config.FOOD_BLOCKING_MODE.get() == Config.FoodBlockingMode.ZERO_NUTRITION &&
                !TimedFoodManager.isTimedFood(itemStack) &&
                !Config.isAlwaysEdible(itemId)) {

            // Обнуляем восстановленный голод и насыщение
            // Это приблизительная реализация - может потребоваться более точное управление
            FoodProperties foodProps = itemStack.get(DataComponents.FOOD);
            if (foodProps != null) {
                player.getFoodData().setFoodLevel(
                        Math.max(0, player.getFoodData().getFoodLevel() - foodProps.nutrition())
                );
                player.getFoodData().setSaturation(
                        Math.max(0, player.getFoodData().getSaturationLevel() - foodProps.saturation())
                );
            }

            LOGGER.debug("Обнулена питательность для еды без временной метки: {} у игрока {}",
                    itemId, player.getName().getString());
        }
    }

    // === СОБЫТИЕ: КОНТЕЙНЕРЫ ===

    /**
     * Обрабатывает открытие контейнеров игроками.
     * Автоматически проверяет содержимое на предмет испорченных предметов.
     */
    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        Level level = player.level();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        if (!Config.ENABLE_SPOILAGE_SYSTEM.get()) {
            return;
        }

        // Пропускаем наш специальный GUI контейнер для еды
        if (event.getContainer() instanceof com.metaphysicsnecrosis.metaphysicsspoilage.gui.FoodContainerMenu) {
            return;
        }

        // Получаем контейнер из меню
        Container container = null;
        if (event.getContainer() instanceof Container directContainer) {
            container = directContainer;
        } else if (event.getContainer().slots.size() > 0 &&
                event.getContainer().getSlot(0) != null &&
                event.getContainer().getSlot(0).container instanceof Container slotContainer) {
            container = slotContainer;
        }

        if (container == null) {
            return;
        }

        UUID playerId = player.getUUID();
        int containerId = container.hashCode();

        // Предотвращаем дублирующие проверки
        Set<Integer> playerContainers = OPEN_CONTAINERS.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet());
        if (playerContainers.contains(containerId)) {
            return;
        }
        playerContainers.add(containerId);

        // Выполняем проверку контейнера в отдельном потоке для производительности
        final Container finalContainer = container;
        final ServerLevel finalServerLevel = serverLevel;
        final String playerName = player.getName().getString();
        new Thread(() -> {
            try {
                LOGGER.debug("Проверка контейнера {} при открытии игроком {}",
                        finalContainer.getClass().getSimpleName(), playerName);

                SpoilageChecker.checkContainerForSpoilage(finalContainer, finalServerLevel);

            } catch (Exception e) {
                LOGGER.error("Ошибка при проверке контейнера на порчу", e);
            }
        }, "SpoilageChecker-Container-" + containerId).start();
    }

    /**
     * Обрабатывает закрытие контейнеров для очистки кэша.
     */
    @SubscribeEvent
    public static void onContainerClose(PlayerContainerEvent.Close event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        UUID playerId = player.getUUID();
        Set<Integer> playerContainers = OPEN_CONTAINERS.get(playerId);
        if (playerContainers != null) {
            // Очищаем все контейнеры игрока при закрытии
            // В более сложной реализации можно отслеживать конкретные контейнеры
            playerContainers.clear();
        }
    }

    // === СОБЫТИЯ: ИГРОКИ ===

    /**
     * Обрабатывает вход игрока на сервер.
     * Инициализирует систему отслеживания для нового игрока.
     */
    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        UUID playerId = player.getUUID();
        LAST_INVENTORY_CHECK.put(playerId, serverTickCounter);
        OPEN_CONTAINERS.put(playerId, ConcurrentHashMap.newKeySet());

        LOGGER.debug("Инициализирована система порчи для игрока: {}", player.getName().getString());

        // Выполняем первоначальную проверку инвентаря
        if (Config.ENABLE_SPOILAGE_SYSTEM.get() && player.level() instanceof ServerLevel serverLevel) {
            new Thread(() -> {
                try {
                    Thread.sleep(1000); // Даем время на полную загрузку игрока
                    SpoilageChecker.checkPlayerInventory(player, serverLevel);
                } catch (Exception e) {
                    LOGGER.error("Ошибка при первоначальной проверке инвентаря игрока", e);
                }
            }, "SpoilageChecker-PlayerInit-" + playerId).start();
        }
    }

    /**
     * Обрабатывает выход игрока с сервера.
     * Очищает данные игрока из кэшей.
     */
    @SubscribeEvent
    public static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        UUID playerId = player.getUUID();
        LAST_INVENTORY_CHECK.remove(playerId);
        OPEN_CONTAINERS.remove(playerId);

        LOGGER.debug("Очищены данные системы порчи для игрока: {}", player.getName().getString());
    }

    // === ПЕРИОДИЧЕСКИЕ ПРОВЕРКИ ===

    /**
     * Обрабатывает тики сервера для выполнения периодических задач.
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Pre event) {
        serverTickCounter++;

        // Выполняем периодические задачи раз в секунду
        if (serverTickCounter % 20 == 0) {
            performPeriodicTasks();
        }
    }

    /**
     * Обрабатывает тики игроков для индивидуальных проверок.
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (!Config.ENABLE_SPOILAGE_SYSTEM.get()) {
            return;
        }

        Level level = player.level();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        UUID playerId = player.getUUID();
        Long lastCheck = LAST_INVENTORY_CHECK.get(playerId);

        // Проверяем, нужно ли проверить инвентарь игрока
        if (lastCheck == null || (serverTickCounter - lastCheck) >= INVENTORY_CHECK_INTERVAL) {
            LAST_INVENTORY_CHECK.put(playerId, serverTickCounter);

            // Выполняем проверку в отдельном потоке
            new Thread(() -> {
                try {
                    SpoilageChecker.checkPlayerInventory(player, serverLevel);
                } catch (Exception e) {
                    LOGGER.error("Ошибка при периодической проверке инвентаря игрока {}",
                            player.getName().getString(), e);
                }
            }, "SpoilageChecker-Periodic-" + playerId).start();
        }
    }

    // === УТИЛИТНЫЕ МЕТОДЫ ===

    /**
     * Выполняет периодические задачи системы порчи.
     */
    private static void performPeriodicTasks() {
        try {
            // Очистка кэшей каждые 5 минут
            if (serverTickCounter % (20 * 60 * 5) == 0) {
                SpoilageChecker.clearCache();
                LOGGER.debug("Выполнена периодическая очистка кэшей системы порчи");
            }

            // Логирование статистики каждые 10 минут
            if (serverTickCounter % (20 * 60 * 10) == 0) {
                SpoilageChecker.SpoilageStatistics stats = SpoilageChecker.getStatistics();
                LOGGER.info("Статистика системы порчи - Проверок: {}, Испорчено: {}, Эффективность кэша: {:.2f}%",
                        stats.getTotalChecks(), stats.getSpoiledItems(), stats.getCacheHitRatio() * 100);
            }

            // Очистка устаревших записей игроков каждый час
            if (serverTickCounter % (20 * 60 * 60) == 0) {
                cleanupPlayerData();
            }

        } catch (Exception e) {
            LOGGER.error("Ошибка при выполнении периодических задач системы порчи", e);
        }
    }

    /**
     * Очищает устаревшие данные игроков.
     */
    private static void cleanupPlayerData() {
        // Удаляем записи игроков, которые давно не были онлайн
        // В реальной реализации можно добавить проверку на активность игроков
        LOGGER.debug("Очистка устаревших данных игроков - до: LAST_CHECK={}, CONTAINERS={}",
                LAST_INVENTORY_CHECK.size(), OPEN_CONTAINERS.size());

        // Простая очистка - можно улучшить логикой проверки активности
        LAST_INVENTORY_CHECK.entrySet().removeIf(entry ->
                (serverTickCounter - entry.getValue()) > (20 * 60 * 60 * 24)); // 24 часа

        LOGGER.debug("Очистка завершена - после: LAST_CHECK={}, CONTAINERS={}",
                LAST_INVENTORY_CHECK.size(), OPEN_CONTAINERS.size());
    }

    // === ПУБЛИЧНЫЕ УТИЛИТНЫЕ МЕТОДЫ ===

    /**
     * Принудительно проверяет инвентари всех онлайн игроков.
     * Используется для админских команд или особых ситуаций.
     */
    public static void forceCheckAllPlayers(ServerLevel level) {
        if (!Config.ENABLE_SPOILAGE_SYSTEM.get()) {
            return;
        }

        List<ServerPlayer> players = level.getServer().getPlayerList().getPlayers();
        LOGGER.info("Начинается принудительная проверка инвентарей {} игроков", players.size());

        for (ServerPlayer player : players) {
            new Thread(() -> {
                try {
                    SpoilageChecker.checkPlayerInventory(player, level);
                } catch (Exception e) {
                    LOGGER.error("Ошибка при принудительной проверке игрока {}",
                            player.getName().getString(), e);
                }
            }, "SpoilageChecker-Force-" + player.getUUID()).start();
        }
    }

    /**
     * Получает статистику работы обработчика событий.
     *
     * @return Строка со статистикой
     */
    public static String getEventHandlerStats() {
        return String.format("EventHandler статистика - Тики: %d, Игроки: %d, Открытые контейнеры: %d",
                serverTickCounter,
                LAST_INVENTORY_CHECK.size(),
                OPEN_CONTAINERS.values().stream().mapToInt(Set::size).sum());
    }

    /**
     * Сбрасывает внутренние счетчики и кэши обработчика.
     * Используется для тестирования или перезагрузки.
     */
    public static void reset() {
        serverTickCounter = 0;
        LAST_INVENTORY_CHECK.clear();
        OPEN_CONTAINERS.clear();
        LOGGER.info("Обработчик событий системы порчи сброшен");
    }
}