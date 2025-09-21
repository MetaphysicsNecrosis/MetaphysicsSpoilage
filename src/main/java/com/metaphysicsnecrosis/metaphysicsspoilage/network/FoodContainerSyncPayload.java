package com.metaphysicsnecrosis.metaphysicsspoilage.network;

import com.metaphysicsnecrosis.metaphysicsspoilage.MetaphysicsSpoilage;
import com.metaphysicsnecrosis.metaphysicsspoilage.items.StoredFoodEntry;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Пакет для синхронизации содержимого FoodContainer между сервером и клиентом.
 * Отправляется с сервера на клиент для обновления GUI.
 */
public record FoodContainerSyncPayload(List<StoredFoodEntry> foods) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<FoodContainerSyncPayload> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MetaphysicsSpoilage.MODID, "food_container_sync"));

    public static final StreamCodec<ByteBuf, FoodContainerSyncPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.collection(java.util.ArrayList::new, StoredFoodEntry.STREAM_CODEC),
        FoodContainerSyncPayload::foods,
        FoodContainerSyncPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}