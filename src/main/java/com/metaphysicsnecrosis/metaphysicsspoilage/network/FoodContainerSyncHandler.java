package com.metaphysicsnecrosis.metaphysicsspoilage.network;

import com.metaphysicsnecrosis.metaphysicsspoilage.gui.FoodContainerMenu;
import com.metaphysicsnecrosis.metaphysicsspoilage.gui.FoodContainerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Обработчик пакета синхронизации FoodContainer на клиентской стороне.
 */
public class FoodContainerSyncHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(FoodContainerSyncHandler.class);

    /**
     * Обрабатывает пакет синхронизации на клиенте
     */
    public static void handleOnMain(final FoodContainerSyncPayload payload, final IPayloadContext context) {
        LocalPlayer player = Minecraft.getInstance().player;

        if (player == null) {
            LOGGER.warn("Получен пакет синхронизации FoodContainer без игрока");
            return;
        }

        // Проверяем, что игрок использует FoodContainer меню
        if (!(player.containerMenu instanceof FoodContainerMenu foodMenu)) {
            LOGGER.debug("Получен пакет синхронизации FoodContainer без открытого FoodContainer меню");
            return;
        }

        // Очищаем старый кэш перед обновлением для предотвращения конфликтов
        foodMenu.clearSyncCache();

        // Обновляем содержимое контейнера в меню
        foodMenu.updateContainerContents(payload.foods());

        // Также обновляем GUI, если оно открыто
        if (Minecraft.getInstance().screen instanceof FoodContainerScreen screen) {
            screen.forceRefreshFromSync();
            // Дополнительно планируем обновление на следующий тик для гарантии
            Minecraft.getInstance().execute(() -> {
                if (Minecraft.getInstance().screen instanceof FoodContainerScreen currentScreen) {
                    currentScreen.scheduleRefresh();
                }
            });
        }

        LOGGER.info("Синхронизировано содержимое FoodContainer: {} записей", payload.foods().size());

        // Дополнительная отладочная информация
        for (var entry : payload.foods()) {
            LOGGER.debug("  - Получено: {} x{} (день {})", entry.itemId(), entry.count(), entry.creationDay());
        }
    }
}