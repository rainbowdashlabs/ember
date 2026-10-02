/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, type Ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {MovementState, type MovementStanding} from '@/api/generated/schema'
import {useMovementParties} from '@/composables/useMovementParties'

/**
 * What a screen needs of a movement to say where it stands. A movement's own row carries these fields
 * and so does a piece of gear something is running on, which is what lets both say it in one set of
 * words.
 */
export type StandingOf = Pick<MovementStanding, 'state' | 'reachedStepLabel' | 'currentStepActor' | 'ownerKind' | 'ownerName'>

/**
 * Where a movement stands, worded once for every screen that shows it: the movement queue, the
 * overview, a member's gear and the gear page of the member themselves.
 *
 * <p>The step named is the last one that has happened, never the one being waited on: a step is named
 * after what it brings about, so wearing the label of the next one says a jacket has been taken back
 * while it is still on the member. Whose turn it is goes beside it, and a movement that is over says
 * how it ended.
 *
 * @param movement the movement, read fresh on every use
 */
export function useMovementStanding(movement: Ref<StandingOf>) {
    const {t} = useI18n()
    const {actorLabel} = useMovementParties(movement)

    const open = computed(() => movement.value.state === MovementState.OPEN)

    /** The last step that has happened, while the movement is still running. */
    const reached = computed(() => open.value ? movement.value.reachedStepLabel : null)

    /** Who is being waited on, or how it ended for one that is over. */
    const standing = computed(() => {
        if (!open.value) return t(`movements.state.${movement.value.state}`)
        const actor = movement.value.currentStepActor
        return actor ? t('movements.waitingFor', {party: actorLabel(actor)}) : ''
    })

    return {open, reached, standing}
}
