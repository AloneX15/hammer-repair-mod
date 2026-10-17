package com.takumistudios.hammer_repair.item;

import com.takumistudios.hammer_repair.HammerRepair;
import com.takumistudios.hammer_repair.config.HammerConfig;
import com.takumistudios.hammer_repair.network.SyncedSettings;
import com.takumistudios.hammer_repair.registry.ModItems;
import com.takumistudios.hammer_repair.repair.ArmorPieces;
import com.takumistudios.hammer_repair.repair.RepairService;
import com.takumistudios.hammer_repair.repair.RepairSessions;
import com.takumistudios.hammer_repair.repair.RepairTargeting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

//? if >=26.1 {
import net.minecraft.world.item.ItemUseAnimation;
//?} else {
/*import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.UseAnim;
*///?}

import java.util.Optional;

import static com.takumistudios.hammer_repair.HammerRepair.LOGGER;

/**
 * El martillo de reparación. Se mantiene pulsado el clic derecho:
 * <ul>
 *   <li>en la mano principal, apuntando a otro jugador: repara su armadura equipada;</li>
 *   <li>en la segunda mano, con una pieza dañada en la principal: repara esa pieza (ver {@link #onUseItem}).</li>
 * </ul>
 * El cliente solo empieza o deja de usar el objeto; el servidor decide qué se repara y cuánto se cobra.
 */
public class RepairHammerItem extends Item {
    /** Duración de uso "infinita": la sesión termina al soltar, al acabar o al interrumpirse. */
    private static final int USE_DURATION = 72000;

    /** Resultado de intentar empezar a usar el martillo, igual en todas las versiones. */
    private enum Start { PASS, FAIL, CONSUME, SUCCESS }

    public RepairHammerItem(Properties properties) {
        super(properties);
    }

    /** Reparación cooperativa: martillo en la mano principal apuntando a otro jugador. */
    private static Start startCooperative(Level level, Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || player.isSpectator()) {
            return Start.PASS;
        }
        try {
            // El cliente predice con el alcance del servidor más un margen: nunca debe ser más estricto que él, porque
            // el paquete de uso se envía igualmente y el servidor podría empezar una sesión que el cliente no conoce
            double reach = level.isClientSide()
                    ? SyncedSettings.clientMaxDistance() + SyncedSettings.CLIENT_REACH_MARGIN
                    : HammerConfig.common().maxDistance();
            Optional<Player> target = RepairTargeting.findTarget(player, reach, level.isClientSide() ? SyncedSettings.CLIENT_AIM_MARGIN : 0.0);
            if (target.isEmpty()) {
                return Start.PASS;
            }
            if (level.isClientSide()) {
                if (!RepairService.canPayAnything(player)) {
                    return Start.FAIL;
                }
            } else if (!(player instanceof ServerPlayer repairer && target.get() instanceof ServerPlayer partner)
                    || !RepairSessions.startTarget(repairer, partner)) {
                return Start.FAIL;
            }
            player.startUsingItem(hand);
            return Start.CONSUME;
        } catch (RuntimeException e) {
            LOGGER.error("Error al empezar una reparación cooperativa", e);
            return Start.FAIL;
        }
    }

    /** Reparación propia: martillo en la segunda mano y pieza dañada en la principal. */
    private static Start startSelf(Player player, Level level, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || player.isSpectator() || !HammerRepair.repairsEnabled()
                || !player.getOffhandItem().is(ModItems.REPAIR_HAMMER)
                || !ArmorPieces.isRepairable(player.getMainHandItem())) {
            return Start.PASS;
        }
        try {
            if (player.isUsingItem()) {
                return Start.CONSUME;
            }
            if (level.isClientSide()) {
                if (!RepairService.canPayAnything(player)) {
                    return Start.FAIL;
                }
            } else if (!(player instanceof ServerPlayer repairer) || !RepairSessions.startSelf(repairer)) {
                return Start.FAIL;
            }
            player.startUsingItem(InteractionHand.OFF_HAND);
            // SUCCESS: en el cliente, Fabric envía el paquete de uso al servidor
            return Start.SUCCESS;
        } catch (RuntimeException e) {
            LOGGER.error("Error al empezar una reparación propia", e);
            return Start.PASS;
        }
    }

    private static void endUse(Level level, LivingEntity entity) {
        if (!level.isClientSide() && entity instanceof ServerPlayer player) {
            RepairSessions.end(player);
        }
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return USE_DURATION;
    }

    //? if >=26.1 {
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        return toResult(startCooperative(level, player, hand));
    }

    /**
     * Se engancha en {@code UseItemCallback} para que la reparación propia tenga prioridad sobre la acción de equipar
     * la pieza; si no se cumplen las condiciones devuelve {@code PASS} y el juego sigue como siempre.
     */
    public static InteractionResult onUseItem(Player player, Level level, InteractionHand hand) {
        return toResult(startSelf(player, level, hand));
    }

    private static InteractionResult toResult(Start start) {
        return switch (start) {
            case PASS -> InteractionResult.PASS;
            case FAIL -> InteractionResult.FAIL;
            case CONSUME -> InteractionResult.CONSUME;
            case SUCCESS -> InteractionResult.SUCCESS;
        };
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        // La animación es el golpe de brazo que el servidor envía en cada reparación completada
        return ItemUseAnimation.NONE;
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remainingTime) {
        endUse(level, entity);
        return false;
    }
    //?} else {
    /*@Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return toResult(startCooperative(level, player, hand), player.getItemInHand(hand));
    }

    // Se engancha en UseItemCallback: ver la versión de 26.x
    public static InteractionResultHolder<ItemStack> onUseItem(Player player, Level level, InteractionHand hand) {
        return toResult(startSelf(player, level, hand), player.getItemInHand(hand));
    }

    private static InteractionResultHolder<ItemStack> toResult(Start start, ItemStack stack) {
        return switch (start) {
            case PASS -> InteractionResultHolder.pass(stack);
            case FAIL -> InteractionResultHolder.fail(stack);
            case CONSUME -> InteractionResultHolder.consume(stack);
            case SUCCESS -> InteractionResultHolder.success(stack);
        };
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        // La animación es el golpe de brazo que el servidor envía en cada reparación completada
        return UseAnim.NONE;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remainingTime) {
        endUse(level, entity);
    }
    *///?}
}
