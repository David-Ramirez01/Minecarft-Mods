package com.tumod.protectormod.util;

import net.minecraft.core.BlockPos;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InviteManager {

    // Usamos ConcurrentHashMap para evitar errores si varios jugadores invitan al mismo tiempo
    private static final Map<UUID, PendingInvite> PENDING_INVITES = new ConcurrentHashMap<>();

    /**
     * Registro de la invitación
     * @param corePos Posición del bloque donde se origina la invitación
     * @param requesterUUID UUID del dueño del core
     * @param expiry Tiempo en milisegundos en el que la invitación dejará de ser válida
     */
    public record PendingInvite(BlockPos corePos, UUID requesterUUID, long expiry) {}

    /**
     * Añade una invitación que durará 60 segundos
     */
    public static void addInvite(UUID targetUUID, BlockPos pos, UUID requester) {
        // 60,000 ms = 1 minuto
        PENDING_INVITES.put(targetUUID, new PendingInvite(pos, requester, System.currentTimeMillis() + 60000));
    }

    /**
     * Obtiene la invitación si no ha expirado
     */
    public static PendingInvite getInvite(UUID uuid) {
        PendingInvite invite = PENDING_INVITES.get(uuid);

        if (invite != null) {
            if (System.currentTimeMillis() > invite.expiry) {
                PENDING_INVITES.remove(uuid); // Eliminar si ya expiró
                return null;
            }
        }
        return invite;
    }

    /**
     * Elimina la invitación manualmente (cuando aceptan o rechazan)
     */
    public static void removeInvite(UUID uuid) {
        PENDING_INVITES.remove(uuid);
    }
}