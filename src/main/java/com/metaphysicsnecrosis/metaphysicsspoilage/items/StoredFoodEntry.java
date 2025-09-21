package com.metaphysicsnecrosis.metaphysicsspoilage.items;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import io.netty.buffer.ByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import com.metaphysicsnecrosis.metaphysicsspoilage.spoilage.SpoilageUtils;

/**
 * Record для хранения информации о еде в FoodContainer.
 * Содержит тип еды, день создания и количество предметов.
 *
 * @param itemId ID предмета еды (например, "minecraft:apple")
 * @param creationDay День создания предмета (long для поддержки неограниченных дней)
 * @param count Количество предметов (до 64 для одного типа)
 */
public record StoredFoodEntry(String itemId, long creationDay, int count) {

    // Codec для сохранения на диск
    public static final Codec<StoredFoodEntry> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.STRING.fieldOf("item_id").forGetter(StoredFoodEntry::itemId),
            Codec.LONG.fieldOf("creation_day").forGetter(StoredFoodEntry::creationDay),
            Codec.INT.fieldOf("count").forGetter(StoredFoodEntry::count)
        ).apply(instance, StoredFoodEntry::new)
    );

    // StreamCodec для сети
    public static final StreamCodec<ByteBuf, StoredFoodEntry> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, StoredFoodEntry::itemId,
        ByteBufCodecs.VAR_LONG, StoredFoodEntry::creationDay,
        ByteBufCodecs.VAR_INT, StoredFoodEntry::count,
        StoredFoodEntry::new
    );

    /**
     * Конструктор с валидацией параметров
     *
     * @param itemId ID предмета (не может быть null или пустым)
     * @param creationDay День создания (должен быть >= 0)
     * @param count Количество предметов (должно быть от 1 до 64)
     */
    public StoredFoodEntry {
        if (itemId == null || itemId.trim().isEmpty()) {
            throw new IllegalArgumentException("ItemId не может быть null или пустым");
        }
        if (creationDay < 0) {
            throw new IllegalArgumentException("День создания не может быть отрицательным");
        }
        if (count < 1 || count > 64) {
            throw new IllegalArgumentException("Количество должно быть от 1 до 64");
        }
    }

    /**
     * Создает новую запись с измененным количеством
     *
     * @param newCount Новое количество предметов
     * @return Новая запись с обновленным количеством
     */
    public StoredFoodEntry withCount(int newCount) {
        return new StoredFoodEntry(itemId, creationDay, newCount);
    }

    /**
     * Проверяет, можно ли объединить эту запись с другой
     * (одинаковые itemId и creationDay)
     *
     * @param other Другая запись для сравнения
     * @return true, если записи можно объединить
     */
    public boolean canCombineWith(StoredFoodEntry other) {
        if (other == null) {
            return false;
        }
        return this.itemId.equals(other.itemId) && this.creationDay == other.creationDay;
    }

    /**
     * Объединяет эту запись с другой, если это возможно
     *
     * @param other Другая запись для объединения
     * @return Новая запись с суммарным количеством, или null если объединение невозможно
     */
    public StoredFoodEntry combineWith(StoredFoodEntry other) {
        if (!canCombineWith(other)) {
            return null;
        }

        int totalCount = this.count + other.count;
        if (totalCount > 64) {
            return null; // Превышен максимальный размер стека
        }

        return new StoredFoodEntry(itemId, creationDay, totalCount);
    }

    /**
     * Проверяет, достаточно ли предметов в записи для извлечения указанного количества
     *
     * @param requestedCount Запрашиваемое количество
     * @return true, если в записи достаточно предметов
     */
    public boolean hasEnough(int requestedCount) {
        return count >= requestedCount;
    }

    /**
     * Создает новую запись с уменьшенным количеством
     *
     * @param removeCount Количество для удаления
     * @return Новая запись с уменьшенным количеством, или null если количество становится <= 0
     */
    public StoredFoodEntry reduce(int removeCount) {
        int newCount = count - removeCount;
        if (newCount <= 0) {
            return null;
        }
        return new StoredFoodEntry(itemId, creationDay, newCount);
    }

    /**
     * Проверяет, пуста ли запись (количество = 0)
     *
     * @return true, если запись пуста
     */
    public boolean isEmpty() {
        return count <= 0;
    }

    /**
     * Создает уникальный ключ для группировки записей
     *
     * @return Ключ в формате "itemId:creationDay"
     */
    public String getGroupKey() {
        return itemId + ":" + creationDay;
    }

    /**
     * Создает ItemStack из этой записи
     *
     * @return ItemStack с установленной временной меткой и количеством
     */
    public ItemStack createItemStack() {
        try {
            ResourceLocation itemLocation = ResourceLocation.parse(itemId);
            Item item = BuiltInRegistries.ITEM.getValue(itemLocation);

            if (item == null) {
                return ItemStack.EMPTY;
            }

            ItemStack stack = new ItemStack(item, count);

            // Устанавливаем временную метку если предмет может портиться
            if (SpoilageUtils.canItemSpoil(item)) {
                SpoilageUtils.setCreationDay(stack, creationDay);
            }

            return stack;
        } catch (Exception e) {
            // Если не удалось создать ItemStack, возвращаем пустой
            return ItemStack.EMPTY;
        }
    }

    @Override
    public String toString() {
        return String.format("StoredFoodEntry{item='%s', day=%d, count=%d}",
                itemId, creationDay, count);
    }
}