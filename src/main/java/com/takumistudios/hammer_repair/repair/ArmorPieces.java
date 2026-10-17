package com.takumistudios.hammer_repair.repair;

import com.takumistudios.hammer_repair.compat.Versions;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Qué piezas puede reparar el martillo: armadura de las cuatro ranuras habituales con durabilidad estándar. */
public final class ArmorPieces {
    /** Orden de desempate de la especificación: casco, pechera, pantalones y botas. */
    public static final List<EquipmentSlot> SLOTS = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);

    private ArmorPieces() {
    }

    /** Armadura (incluida la de otros mods) para casco, pechera, pantalones o botas. Excluye élitros y similares. */
    public static boolean isEligible(ItemStack stack) {
        if (stack.isEmpty() || !stack.isDamageableItem()) {
            return false;
        }
        EquipmentSlot slot = Versions.armorSlot(stack);
        return slot != null && SLOTS.contains(slot);
    }

    public static boolean isRepairable(ItemStack stack) {
        return isEligible(stack) && stack.isDamaged();
    }

    /** Pieza equipada en {@code slot} si es reparable por el martillo; si no, {@link ItemStack#EMPTY}. */
    public static ItemStack worn(Player player, EquipmentSlot slot) {
        ItemStack stack = player.getItemBySlot(slot);
        if (!isEligible(stack)) {
            return ItemStack.EMPTY;
        }
        // La pieza debe ir en su propia ranura (una pechera no cuenta si un mod la pone en la cabeza)
        return Versions.armorSlot(stack) == slot ? stack : ItemStack.EMPTY;
    }

    /** Ranura de la pieza equipada más desgastada (por porcentaje) o {@code null} si no hay ninguna dañada. */
    public static EquipmentSlot mostWorn(Player player) {
        List<RepairMath.Piece> pieces = new ArrayList<>(SLOTS.size());
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = worn(player, slot);
            pieces.add(stack.isEmpty() ? null : new RepairMath.Piece(stack.getDamageValue(), stack.getMaxDamage()));
        }
        int index = RepairMath.selectPiece(pieces);
        return index < 0 ? null : SLOTS.get(index);
    }

    public static boolean hasDamagedArmor(Player player) {
        return mostWorn(player) != null;
    }
}
