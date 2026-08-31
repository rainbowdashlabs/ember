/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {inventoryCollections} from '@/api'

/**
 * What a station stands to lose from its collections when a piece, a kind or a drawer goes.
 *
 * <p>The line goes with the thing it names, and that is the only sensible behaviour: a collection
 * cannot keep pointing at a piece, a kind or a drawer nobody has any more. What makes it acceptable
 * is that the loss is not silent, so every screen that offers the deletion asks here first and puts
 * the names into the question it is about to ask.
 *
 * <p>Removing a kind is the one worth reading twice: the pieces all stay where they are, so nothing
 * on the screen looks any different afterwards, and the line that asked for four blue ones is gone.
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

    /** Reads the collections that ask for a kind of thing, ahead of removing it. */
    async function forArt(inventoryId: number, artId: number) {
        await load(inventoryCollections.askingForArt(inventoryId, artId), 'inventory.collections.artDeleteWarning')
    }

    /** Reads the collections that draw from an inventory, or name one of its pieces or kinds. */
    async function forInventory(inventoryId: number) {
        await load(inventoryCollections.touchingInventory(inventoryId), 'inventory.collections.inventoryDeleteWarning')
    }

    return {warning, clear, forItem, forArt, forInventory}
}
