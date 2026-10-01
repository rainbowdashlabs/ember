/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, type Ref} from 'vue'
import {glyphFor} from '@/util/glyph'
import {useMovementParties} from '@/composables/useMovementParties'
import type {MovementResponse} from '@/api/generated/schema'

/**
 * What one movement looks like in a list, whichever list it stands in.
 *
 * <p>The queue and the inventory overview draw the same cells for the same movement. Working them
 * out twice is how the two drift apart. Where it stands is worded by `useMovementStanding`, which the
 * gear a movement runs on shares.
 *
 * @param movement the movement being drawn
 */
export function useMovementRowView(movement: Ref<MovementResponse>) {
    const {ownerLabel} = useMovementParties(movement)

    const chip = computed(() => ({
        glyph: glyphFor({icon: movement.value.icon, color: movement.value.color}),
        name: movement.value.itemName ?? movement.value.incomingItemName ?? movement.value.inventoryName ?? '',
        internalId: movement.value.itemInternalId,
        sizeName: movement.value.itemSizeName,
        replacedSize: movement.value.newSizeName ? movement.value.oldSizeName : null,
        inventoryName: movement.value.itemName ? movement.value.inventoryName : null,
    }))

    /**
     * Whose gear it is, said on every row.
     *
     * <p>It sits under the kind of movement because the two together are what a row is: a swap of the
     * station's own gear and a swap of the association's walk different chains and end in different
     * places, and telling them apart mattered enough to be worth a line of its own.
     */
    const owner = computed(() => ownerLabel.value)

    return {chip, owner}
}
