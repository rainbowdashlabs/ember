/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, type Ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {glyphFor} from '@/util/glyph'
import {formatDate} from '@/util/format'
import {useMovementParties} from '@/composables/useMovementParties'
import {MovementState, type Movement} from '@/api/movements'

/**
 * What one movement looks like in a list, whichever shape the list has.
 *
 * <p>A wide screen reads it as a row of columns and a narrow one as a card, and both say the same
 * things about the same movement. Working them out twice is how the two drift apart.
 *
 * @param movement the movement being drawn
 */
export function useMovementRowView(movement: Ref<Movement>) {
    const {t} = useI18n()
    const {ownerLabel, actorLabel} = useMovementParties(movement)

    const chip = computed(() => ({
        glyph: glyphFor({icon: movement.value.icon, color: movement.value.color}),
        name: movement.value.itemName ?? movement.value.incomingItemName ?? movement.value.inventoryName ?? '',
        internalId: movement.value.itemInternalId,
        sizeName: movement.value.itemSizeName,
    }))

    const open = computed(() => movement.value.state === MovementState.OPEN)

    /**
     * Whose gear it is, said on every row.
     *
     * <p>It sits under the kind of movement because the two together are what a row is: a swap of the
     * station's own gear and a swap of the association's walk different chains and end in different
     * places, and telling them apart mattered enough to be worth a line of its own.
     */
    const owner = computed(() => ownerLabel.value)

    const startedOn = computed(() => formatDate(movement.value.createdAt))

    /**
     * When it last moved, said only where that is not the day it started: a row that has never moved
     * would otherwise carry the same date twice, which reads as two facts and is one.
     */
    const movedOn = computed(() => {
        const moved = movement.value.updatedAt
        if (!moved) return ''
        const said = formatDate(moved)
        return said === startedOn.value ? '' : said
    })

    /** Who is being waited on, or how it ended for one that is over. */
    const standing = computed(() => {
        if (!open.value) return t(`movements.state.${movement.value.state}`)
        const actor = movement.value.currentStepActor
        return actor ? t('movements.waitingFor', {party: actorLabel(actor)}) : ''
    })

    return {chip, open, owner, startedOn, movedOn, standing}
}
