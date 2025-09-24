package com.metaphysicsnecrosis.metaphysicsspoilage.mixin;

import com.metaphysicsnecrosis.metaphysicsspoilage.component.SpoilageComponent;
import com.metaphysicsnecrosis.metaphysicsspoilage.component.SpoilageHooks;
import com.metaphysicsnecrosis.metaphysicsspoilage.MetaphysicsSpoilage;
import com.metaphysicsnecrosis.metaphysicsspoilage.spoilage.SpoilageUtils;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin для автоматического присваивания временных меток еде при создании ItemStack.
 * Заменяет тяжелую систему отслеживания событий более легким подходом на уровне конструктора.
 *
 * Основан на подходе TerraFirmaCraft для эффективного управления компонентами.
 *
 * @author MetaphysicsNecrosis
 * @version 1.0
 * @since 1.21.8
 */
@Mixin(ItemStack.class)
public class ItemStackMixin {

    /**
     * Автоматически добавляет SpoilageComponent к еде при создании ItemStack.
     * Вызывается для ВСЕХ создаваемых ItemStack, но обрабатывает только еду.
     *
     * Это более эффективно чем отслеживание множества игровых событий,
     * так как работает на самом низком уровне создания предметов.
     */
    @Inject(
        method = "<init>(Lnet/minecraft/world/level/ItemLike;ILnet/minecraft/core/component/PatchedDataComponentMap;)V",
        at = @At("TAIL")
    )
    private void onItemStackCreate(CallbackInfo ci) {
        ItemStack stack = (ItemStack) (Object) this;
        SpoilageHooks.onItemStackCreated(stack);
    }

    /**
     * Обрабатывает копирование ItemStack - превращает TRANSIENT_NEVER_DECAY_FLAG в текущее время
     */
    @Inject(
        method = "copy()Lnet/minecraft/world/item/ItemStack;",
        at = @At("RETURN")
    )
    private void onCopy(CallbackInfoReturnable<ItemStack> cir) {
        ItemStack originalStack = (ItemStack) (Object) this;
        ItemStack copiedStack = cir.getReturnValue();

        // Проверяем, есть ли у оригинала компонент порчи
        if (SpoilageUtils.hasTimestamp(originalStack)) {
            long creationDay = SpoilageUtils.getCreationDay(originalStack);

            // Если у оригинала флаг TRANSIENT_NEVER_DECAY_FLAG, заменяем его на текущее время в копии
            if (creationDay == SpoilageComponent.TRANSIENT_NEVER_DECAY_FLAG) {
                SpoilageHooks.setFreshTimestamp(copiedStack);
            }
        }
    }
}