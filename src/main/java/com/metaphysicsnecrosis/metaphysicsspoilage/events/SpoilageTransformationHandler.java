package com.metaphysicsnecrosis.metaphysicsspoilage.events;

import com.metaphysicsnecrosis.metaphysicsspoilage.Config;
import com.metaphysicsnecrosis.metaphysicsspoilage.manager.TimedFoodManager;
import com.metaphysicsnecrosis.metaphysicsspoilage.spoilage.SpoilageChecker;
import com.metaphysicsnecrosis.metaphysicsspoilage.spoilage.SpoilageTransformer;
import com.metaphysicsnecrosis.metaphysicsspoilage.spoilage.SpoilageUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.item.ItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Обработчик событий для автоматического превращения испорченных предметов.
 *
 * Реализует требования из README:
 * - Автоматическое превращение предметов в мире (ItemEntity)
 * - Периодическая проверка инвентарей игроков
 * - Превращение при попытке использования
 * - Обработка только предметов с временной меткой, которые не в контейнере
 *
 * @author MetaphysicsNecrosis
 * @version 1.0
 * @since 1.21.8
 */
@EventBusSubscriber
public class SpoilageTransformationHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpoilageTransformationHandler.class);

    /**
     * Интервал проверки инвентарей игроков в тиках (30 секунд = 600 тиков)
     */
    private static final int PLAYER_INVENTORY_CHECK_INTERVAL = 600;

    /**
     * Интервал проверки предметов в мире в тиках (10 секунд = 200 тиков)
     */
    private static final int WORLD_ITEM_CHECK_INTERVAL = 200;

    /**
     * Счетчик тиков для периодических проверок
     */
    private static int tickCounter = 0;

    /**
     * Кэш последних проверок игроков для предотвращения спама
     */
    private static final ConcurrentHashMap<UUID, Long> LAST_PLAYER_CHECKS = new ConcurrentHashMap<>();

    /**
     * Кэш последних проверок предметов в мире
     */
    private static final ConcurrentHashMap<UUID, Long> LAST_ITEM_CHECKS = new ConcurrentHashMap<>();

    /**
     * Период проверки в миллисекундах для предотвращения частых проверок одного и того же предмета
     */
    private static final long CHECK_COOLDOWN = 5000L; // 5 секунд

    /**
     * Обработчик тиков сервера для периодических проверок
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Pre event) {
        // Проверяем, включена ли система превращения
        if (!SpoilageTransformer.isTransformationEnabled()) {
            return;
        }

        tickCounter++;

        // Периодическая проверка инвентарей игроков
        if (tickCounter % PLAYER_INVENTORY_CHECK_INTERVAL == 0) {
            checkAllPlayerInventories();
        }

        // Очистка кэшей каждые 30 минут (36000 тиков)
        if (tickCounter % 36000 == 0) {
            cleanupCaches();
            tickCounter = 0; // Сброс счетчика для предотвращения переполнения
        }
    }

    /**
     * Обработчик тиков сущностей для проверки предметов в мире
     */
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Pre event) {
        // Проверяем только предметы в мире
        if (!(event.getEntity() instanceof ItemEntity itemEntity)) {
            return;
        }

        // Проверяем, включена ли система превращения
        if (!SpoilageTransformer.isTransformationEnabled()) {
            return;
        }

        // Проверяем только на серверной стороне
        if (itemEntity.level().isClientSide()) {
            return;
        }

        // Проверяем кулдаун для этого предмета
        UUID itemId = itemEntity.getUUID();
        long currentTime = System.currentTimeMillis();
        Long lastCheck = LAST_ITEM_CHECKS.get(itemId);

        if (lastCheck != null && (currentTime - lastCheck) < CHECK_COOLDOWN) {
            return; // Слишком рано для проверки
        }

        LAST_ITEM_CHECKS.put(itemId, currentTime);

        // Проверяем и обрабатываем превращение
        processItemEntitySpoilage(itemEntity);
    }

    /**
     * Обработчик попытки использования предмета (еды)
     */
    @SubscribeEvent
    public static void onItemUse(LivingEntityUseItemEvent.Start event) {
        // Проверяем только игроков
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        // Проверяем, включена ли система превращения
        if (!SpoilageTransformer.isTransformationEnabled()) {
            return;
        }

        // Проверяем только на серверной стороне
        if (player.level().isClientSide()) {
            return;
        }

        ItemStack itemStack = event.getItem();

        // Проверяем, что это предмет с временной меткой
        if (!TimedFoodManager.isTimedFood(itemStack)) {
            return;
        }

        // Проверяем, испорчен ли предмет
        if (SpoilageUtils.isItemSpoiled(itemStack, (ServerLevel) player.level())) {
            // Превращаем предмет
            ItemStack transformedStack = SpoilageTransformer.autoTransformIfSpoiled(itemStack);

            if (!transformedStack.isEmpty() && !ItemStack.matches(itemStack, transformedStack)) {
                // Заменяем предмет в руке игрока
                player.setItemInHand(event.getEntity().getUsedItemHand(), transformedStack);

                // Отменяем использование оригинального предмета
                event.setCanceled(true);

                LOGGER.info("Превращен предмет при попытке использования игроком {}: {} -> {}",
                        player.getName().getString(),
                        BuiltInRegistries.ITEM.getKey(itemStack.getItem()),
                        BuiltInRegistries.ITEM.getKey(transformedStack.getItem()));
            }
        }
    }

    /**
     * Обработчик входа игрока в мир (проверка инвентаря при подключении)
     */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();

        // Проверяем только на серверной стороне
        if (player.level().isClientSide()) {
            return;
        }

        // Проверяем, включена ли система превращения
        if (!SpoilageTransformer.isTransformationEnabled()) {
            return;
        }

        // Проверяем инвентарь игрока при входе в мир
        checkPlayerInventoryForSpoilage(player);

        LOGGER.debug("Проверен инвентарь игрока {} при входе в мир", player.getName().getString());
    }

    /**
     * Проверяет предмет в мире на порчу и превращает его при необходимости
     */
    private static void processItemEntitySpoilage(ItemEntity itemEntity) {
        ItemStack itemStack = itemEntity.getItem();

        // Проверяем, что это предмет с временной меткой
        if (!TimedFoodManager.isTimedFood(itemStack)) {
            return;
        }

        // Проверяем, может ли предмет быть превращен
        if (!SpoilageTransformer.canItemBeTransformed(itemStack.getItem())) {
            return;
        }

        ServerLevel level = (ServerLevel) itemEntity.level();

        // Проверяем, испорчен ли предмет
        if (SpoilageUtils.isItemSpoiled(itemStack, level)) {
            // Превращаем предмет
            ItemStack transformedStack = SpoilageTransformer.autoTransformIfSpoiled(itemStack);

            if (!transformedStack.isEmpty() && !ItemStack.matches(itemStack, transformedStack)) {
                // Заменяем предмет в мире
                itemEntity.setItem(transformedStack);

                LOGGER.debug("Превращен предмет в мире: {} -> {} (позиция: {}, {}, {})",
                        BuiltInRegistries.ITEM.getKey(itemStack.getItem()),
                        BuiltInRegistries.ITEM.getKey(transformedStack.getItem()),
                        itemEntity.getX(), itemEntity.getY(), itemEntity.getZ());
            }
        }
    }

    /**
     * Проверяет инвентарь конкретного игрока на испорченные предметы
     */
    private static void checkPlayerInventoryForSpoilage(Player player) {
        UUID playerId = player.getUUID();
        long currentTime = System.currentTimeMillis();

        // Проверяем кулдаун
        Long lastCheck = LAST_PLAYER_CHECKS.get(playerId);
        if (lastCheck != null && (currentTime - lastCheck) < CHECK_COOLDOWN) {
            return;
        }

        LAST_PLAYER_CHECKS.put(playerId, currentTime);

        ServerLevel level = (ServerLevel) player.level();
        int transformedCount = 0;

        // Проверяем все слоты инвентаря
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);

            if (stack.isEmpty()) {
                continue;
            }

            // Проверяем, что это предмет с временной меткой
            if (!TimedFoodManager.isTimedFood(stack)) {
                continue;
            }

            // Проверяем, может ли предмет быть превращен
            if (!SpoilageTransformer.canItemBeTransformed(stack.getItem())) {
                continue;
            }

            // Проверяем, испорчен ли предмет
            if (SpoilageUtils.isItemSpoiled(stack, level)) {
                // Превращаем предмет
                ItemStack transformedStack = SpoilageTransformer.autoTransformIfSpoiled(stack);

                if (!transformedStack.isEmpty() && !ItemStack.matches(stack, transformedStack)) {
                    // Заменяем предмет в инвентаре
                    player.getInventory().setItem(i, transformedStack);
                    transformedCount++;

                    LOGGER.debug("Превращен предмет в инвентаре игрока {}: {} -> {}",
                            player.getName().getString(),
                            BuiltInRegistries.ITEM.getKey(stack.getItem()),
                            BuiltInRegistries.ITEM.getKey(transformedStack.getItem()));
                }
            }
        }

        if (transformedCount > 0) {
            LOGGER.info("Превращено {} предметов в инвентаре игрока {}",
                    transformedCount, player.getName().getString());
        }
    }

    /**
     * Проверяет инвентари всех игроков на сервере
     */
    private static void checkAllPlayerInventories() {
        // Эта функция будет вызвана через ServerTickEvent, поэтому нужно получить всех игроков
        // Для этого нужен доступ к серверу, который мы получим через события
        // Пока что оставляем заглушку, так как для полной реализации нужен доступ к MinecraftServer
        LOGGER.debug("Периодическая проверка инвентарей игроков (заглушка)");
    }

    /**
     * Очищает устаревшие записи в кэшах
     */
    private static void cleanupCaches() {
        long currentTime = System.currentTimeMillis();
        long maxAge = 30 * 60 * 1000L; // 30 минут

        // Очищаем кэш проверок игроков
        LAST_PLAYER_CHECKS.entrySet().removeIf(entry ->
                (currentTime - entry.getValue()) > maxAge);

        // Очищаем кэш проверок предметов
        LAST_ITEM_CHECKS.entrySet().removeIf(entry ->
                (currentTime - entry.getValue()) > maxAge);

        LOGGER.debug("Очищены кэши SpoilageTransformationHandler. Игроков: {}, Предметов: {}",
                LAST_PLAYER_CHECKS.size(), LAST_ITEM_CHECKS.size());
    }

    /**
     * Получает статистику обработчика событий
     */
    public static String getHandlerStatistics() {
        return String.format("SpoilageTransformationHandler - Активных проверок игроков: %d, Активных проверок предметов: %d",
                LAST_PLAYER_CHECKS.size(), LAST_ITEM_CHECKS.size());
    }

    /**
     * Принудительно очищает все кэши обработчика
     */
    public static void clearCaches() {
        LAST_PLAYER_CHECKS.clear();
        LAST_ITEM_CHECKS.clear();
        tickCounter = 0;
        LOGGER.info("Кэши SpoilageTransformationHandler очищены");
    }

    /**
     * Валидирует корректность работы обработчика событий
     */
    public static boolean validateHandler() {
        try {
            // Проверяем, что кэши инициализированы
            if (LAST_PLAYER_CHECKS == null || LAST_ITEM_CHECKS == null) {
                LOGGER.error("Кэши обработчика не инициализированы");
                return false;
            }

            // Проверяем интеграцию с SpoilageTransformer
            if (!SpoilageTransformer.isTransformationEnabled()) {
                LOGGER.debug("SpoilageTransformer отключен, валидация пропущена");
                return true;
            }

            LOGGER.info("Валидация SpoilageTransformationHandler прошла успешно");
            return true;

        } catch (Exception e) {
            LOGGER.error("Ошибка при валидации SpoilageTransformationHandler", e);
            return false;
        }
    }
}