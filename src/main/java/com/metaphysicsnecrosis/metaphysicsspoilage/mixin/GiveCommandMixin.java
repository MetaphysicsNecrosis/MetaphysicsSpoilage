package com.metaphysicsnecrosis.metaphysicsspoilage.mixin;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.GiveCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin для обработки команды /give.
 * Устанавливает свежие временные метки для еды, выдаваемой через команду /give.
 *
 * @author MetaphysicsNecrosis
 * @version 1.0
 * @since 1.21.8
 */
@Mixin(GiveCommand.class)
public class GiveCommandMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger(GiveCommandMixin.class);

    /**
     * Примечание: Этот Mixin служит для логирования и резервной обработки.
     * Основная логика обработки команды /give происходит в PlayerInventoryMixin,
     * который перехватывает все добавления предметов в инвентарь игрока,
     * включая команды, торговлю и другие источники.
     *
     * Данный класс оставлен для совместимости и возможных будущих улучшений.
     */
    static {
        LOGGER.info("GiveCommandMixin загружен. Основная обработка выполняется через PlayerInventoryMixin.");
    }
}