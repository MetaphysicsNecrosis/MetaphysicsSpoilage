package com.metaphysicsnecrosis.metaphysicsspoilage.network;

import com.metaphysicsnecrosis.metaphysicsspoilage.gui.FoodContainerMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Обработчик пакетов для команд извлечения еды из контейнера.
 * Выполняется на серверной стороне.
 */
public class FoodContainerPayloadHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(FoodContainerPayloadHandler.class);

    /**
     * Обрабатывает пакет извлечения еды на основном потоке сервера
     */
    public static void handleOnMain(final FoodContainerPayload payload, final IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();

        // Проверяем, что игрок использует FoodContainer меню
        if (!(player.containerMenu instanceof FoodContainerMenu foodMenu)) {
            LOGGER.warn("Получен пакет FoodContainer от игрока {} без открытого FoodContainer меню", player.getName().getString());
            return;
        }

        boolean success = false;
        String actionDescription = "";

        try {
            switch (payload.action()) {
                case EXTRACT_SPECIFIC -> {
                    success = foodMenu.extractFood(payload.itemId(), payload.count());
                    actionDescription = String.format("извлечение %d x %s", payload.count(), payload.itemId());
                }
                case EXTRACT_ALL_TYPE -> {
                    success = foodMenu.extractAllFood(payload.itemId());
                    actionDescription = String.format("извлечение всех %s", payload.itemId());
                }
                case EXTRACT_OLDEST -> {
                    success = foodMenu.extractOldestFood(payload.count());
                    actionDescription = String.format("извлечение %d самых старых предметов", payload.count());
                }
                case EXTRACT_ALL -> {
                    // Извлекаем все предметы в цикле
                    int extracted = 0;
                    boolean keepExtracting = true;

                    while (keepExtracting && !foodMenu.isEmpty() && extracted < 1000) { // Ограничение для предотвращения бесконечного цикла
                        boolean extractedSomething = foodMenu.extractOldestFood(64);
                        if (!extractedSomething) {
                            keepExtracting = false;
                        } else {
                            extracted++;
                        }

                        // Синхронизируем после каждых 5 операций для лучшей отзывчивости UI
                        if (extracted % 5 == 0) {
                            foodMenu.refreshContainer();
                        }
                    }

                    // Финальная синхронизация для обеспечения корректного отображения
                    foodMenu.refreshContainer();

                    success = extracted > 0;
                    actionDescription = String.format("извлечение всех предметов (%d операций)", extracted);
                }
            }

            if (success) {
                LOGGER.debug("Игрок {} успешно выполнил: {}", player.getName().getString(), actionDescription);

                // Обновляем меню для синхронизации с клиентом
                foodMenu.refreshContainer();
                // Принудительно уведомляем клиент об изменениях в слотах
                foodMenu.slotsChanged(null);
            } else {
                LOGGER.debug("Игрок {} не смог выполнить: {}", player.getName().getString(), actionDescription);
            }

        } catch (Exception e) {
            LOGGER.error("Ошибка при обработке пакета FoodContainer от игрока {}: {}",
                player.getName().getString(), e.getMessage());

            // Отключаем игрока в случае серьезной ошибки
            player.connection.disconnect(Component.translatable("metaphysicsspoilage.network.error"));
        }
    }
}