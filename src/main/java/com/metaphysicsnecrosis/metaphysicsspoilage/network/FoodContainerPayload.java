package com.metaphysicsnecrosis.metaphysicsspoilage.network;

import com.metaphysicsnecrosis.metaphysicsspoilage.MetaphysicsSpoilage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Пакет для команд извлечения еды из контейнера.
 * Отправляется с клиента на сервер для выполнения операций извлечения.
 */
public record FoodContainerPayload(ActionType action, String itemId, int count) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<FoodContainerPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MetaphysicsSpoilage.MODID, "food_container"));

    public static final StreamCodec<ByteBuf, FoodContainerPayload> STREAM_CODEC = StreamCodec.composite(
        ActionType.STREAM_CODEC,
        FoodContainerPayload::action,
        ByteBufCodecs.STRING_UTF8,
        FoodContainerPayload::itemId,
        ByteBufCodecs.VAR_INT,
        FoodContainerPayload::count,
        FoodContainerPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Типы действий для извлечения еды
     */
    public enum ActionType {
        EXTRACT_SPECIFIC,   // Извлечь определенное количество определенного предмета
        EXTRACT_ALL_TYPE,   // Извлечь все предметы определенного типа
        EXTRACT_OLDEST,     // Извлечь самую старую еду
        EXTRACT_ALL;        // Извлечь все предметы

        public static final StreamCodec<ByteBuf, ActionType> STREAM_CODEC = StreamCodec.of(
            (buf, action) -> buf.writeByte(action.ordinal()),
            buf -> ActionType.values()[buf.readByte()]
        );
    }

    /**
     * Создает пакет для извлечения определенного количества конкретного предмета
     */
    public static FoodContainerPayload extractSpecific(String itemId, int count) {
        return new FoodContainerPayload(ActionType.EXTRACT_SPECIFIC, itemId, count);
    }

    /**
     * Создает пакет для извлечения всех предметов определенного типа
     */
    public static FoodContainerPayload extractAllType(String itemId) {
        return new FoodContainerPayload(ActionType.EXTRACT_ALL_TYPE, itemId, 0);
    }

    /**
     * Создает пакет для извлечения самой старой еды
     */
    public static FoodContainerPayload extractOldest(int count) {
        return new FoodContainerPayload(ActionType.EXTRACT_OLDEST, "", count);
    }

    /**
     * Создает пакет для извлечения всех предметов
     */
    public static FoodContainerPayload extractAll() {
        return new FoodContainerPayload(ActionType.EXTRACT_ALL, "", 0);
    }
}