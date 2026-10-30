/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref, watch} from 'vue'
import {inventory} from '@/api'
import {
    InventoryType,
    ItemCustody,
    ItemOwner,
    StationPermission,
    type InventoryItem,
} from '@/api/generated/schema'
import {useSession} from '@/composables/useSession'

/**
 * Who owns a piece of a mixed inventory, chosen while the piece is being edited.
 *
 * <p>Offered only in a mixed inventory: an internal or external one says by its type whose gear it holds.
 * Changeable only while the piece rests in the station's store, because one that is with somebody, on its
 * way or missing has a chain or a record running for the owner it has. And only for whoever may write gear
 * down for both owners.
 */
export function useItemOwnerChoice(item: () => InventoryItem | null) {
    const {hasPermission} = useSession()
    const mixed = ref(false)
    const owner = ref<ItemOwner | null>(item()?.ownerKind ?? null)

    watch(() => item()?.inventoryId, async (inventoryId) => {
        mixed.value = false
        if (inventoryId == null) return
        try {
            mixed.value = (await inventory.getInventory(inventoryId)).inventoryType === InventoryType.MIXED
        } catch {
            mixed.value = false
        }
    }, {immediate: true})

    watch(() => item()?.ownerKind, reset)

    const offered = computed(() => {
        const current = item()
        return current != null
            && mixed.value
            && current.ownerKind !== ItemOwner.PARTNER_STATION
            && hasPermission(StationPermission.INVENTORY_CREATE_INTERNAL)
            && hasPermission(StationPermission.INVENTORY_CREATE_EXTERNAL)
    })

    const atRest = computed(() => {
        const current = item()
        if (!current) return false
        return (current.ownerKind === ItemOwner.STATION && current.custody === ItemCustody.WITH_OWNER)
            || (current.ownerKind === ItemOwner.CLUSTER && current.custody === ItemCustody.AT_STATION)
    })

    /** Puts the choice back on the owner the piece has. */
    function reset() {
        owner.value = item()?.ownerKind ?? null
    }

    /** Hands the piece to the chosen owner, or returns nothing when the choice leaves it where it is. */
    async function apply(): Promise<InventoryItem | null> {
        const current = item()
        if (!current || !offered.value || !atRest.value || owner.value == null) return null
        if (owner.value === current.ownerKind) return null
        return inventory.changeOwner(current.id, owner.value)
    }

    return {offered, atRest, owner, reset, apply}
}
