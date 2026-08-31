/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {inventoryCollections} from '@/api'

/**
 * What a station stands to lose from its collections when a piece or a drawer goes.
 *
 * <p>The line goes with the thing it names, and that is the only sensible behaviour: a collection
 * cannot keep pointing at a piece nobody has any more. What makes it acceptable is that the loss is
 * not silent, so every screen that offers the deletion asks here first and puts the names into the
 * question it is about to ask.
 *
 * <p>A failed lookup leaves the warning empty rather than blocking the deletion. Somebody about to
 * delete a piece should not be stopped by a list that could not be read.
 */
export function useCollectionLossWarning() {
    const {t} = useI18n()
    const warning = ref('')

    function clear() {
        warning.value = ''
    }

    async function load(names: Promise<string[]>, key: string) {
        clear()
        try {
            const found = await names
            if (found.length > 0) warning.value = ' ' + t(key, {names: found.join(', ')})
        } catch {
            clear()
        }
    }

    /** Reads the collections that name a piece, ahead of deleting it. */
    async function forItem(itemId: number) {
        await load(inventoryCollections.holdingItem(itemId), 'inventory.collections.deleteWarning')
    }

    /** Reads the collections that draw from an inventory, or name one of its pieces. */
    async function forInventory(inventoryId: number) {
        await load(inventoryCollections.touchingInventory(inventoryId), 'inventory.collections.inventoryDeleteWarning')
    }

    return {warning, clear, forItem, forInventory}
}
