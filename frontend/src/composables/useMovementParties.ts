/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, type Ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {ItemOwner} from '@/api/inventory'
import {StepActor, type Movement, type StepActorName} from '@/api/movements'

/** What a screen needs of a movement to name the parties it is between. */
export type PartiesOf = Pick<Movement, 'ownerKind' | 'ownerName'>

/**
 * The parties of one movement, by name rather than by role.
 *
 * <p>"The owner" is true of every movement and tells nobody anything: the person holding a pair of
 * gloves cannot tell from it whether the gloves are the station's or the association's, which is the
 * one thing that decides where they go back to. Where the association is on this instance it has a
 * name, and that name belongs on every place the role was written.
 *
 * @param movement the movement whose parties are being named, read fresh on every use
 */
export function useMovementParties(movement: Ref<PartiesOf | null | undefined>) {
    const {t} = useI18n()

    const ownerLabel = computed(() => {
        const of = movement.value
        if (!of || of.ownerKind !== ItemOwner.CLUSTER) return t('movements.owner.station')
        return of.ownerName ?? t('movements.owner.above')
    })

    /** The party whose step this is, with the owner named where it has a name. */
    function actorLabel(actor: StepActorName): string {
        return actor === StepActor.OWNER ? ownerLabel.value : t(`movements.actor.${actor}`)
    }

    return {ownerLabel, actorLabel}
}
