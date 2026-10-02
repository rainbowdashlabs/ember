/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {mount} from '@vue/test-utils'
import {defineComponent, ref} from 'vue'
import {describe, expect, it} from 'vitest'
import {
    ItemOwner,
    MovementState,
    StepActor,
    type MovementResponse,
    type MyInventoryItem,
} from '@/api/generated/schema'
import MovementStandingBadges from '@/components/inventory/MovementStandingBadges.vue'
import {useMovementStanding, type StandingOf} from './useMovementStanding'

function standing(fields: Partial<StandingOf> = {}): StandingOf {
    return {
        state: MovementState.OPEN,
        reachedStepLabel: 'Tausch angefordert',
        currentStepActor: StepActor.STATION,
        ownerKind: ItemOwner.STATION,
        ownerName: null,
        ...fields,
    }
}

function worded(of: StandingOf) {
    let result: ReturnType<typeof useMovementStanding> | null = null
    mount(defineComponent({
        setup() {
            result = useMovementStanding(ref(of))
            return () => null
        },
    }))
    const words = result as unknown as ReturnType<typeof useMovementStanding>
    return {open: words.open.value, reached: words.reached.value, standing: words.standing.value}
}

function badges(of: StandingOf) {
    const wrapper = mount(MovementStandingBadges, {props: {movement: of}})
    const text = (id: string) => {
        const found = wrapper.find(`[data-testid="${id}"]`)
        return found.exists() ? found.text() : null
    }
    return {step: text('movement-step'), turn: text('movement-turn'), state: text('movement-state')}
}

describe('useMovementStanding', () => {
    it('names the step that happened and whose turn it is', () => {
        expect(worded(standing())).toEqual({open: true, reached: 'Tausch angefordert', standing: 'Wartet auf: Wache'})
    })

    it('names the association by name where it is waited on', () => {
        const ofTheAssociation = standing({
            currentStepActor: StepActor.OWNER, ownerKind: ItemOwner.CLUSTER, ownerName: 'Kreisverband',
        })

        expect(worded(ofTheAssociation).standing).toBe('Wartet auf: Kreisverband')
    })

    it('says how a movement ended instead of a step', () => {
        const ended = standing({state: MovementState.DECLINED, reachedStepLabel: 'Ersatz bereit', currentStepActor: null})

        expect(worded(ended)).toEqual({open: false, reached: null, standing: 'Abgelehnt'})
    })

    it('says nothing about a step at the very beginning of a chain', () => {
        expect(worded(standing({reachedStepLabel: null})).reached).toBeNull()
    })
})

describe('MovementStandingBadges', () => {
    it('draws a movement row and the piece it runs on with the same words', () => {
        const row = {
            id: 4, state: MovementState.OPEN, reachedStepLabel: 'Ersatz ausgegeben', currentStepActor: StepActor.MEMBER,
            currentStepLabel: 'Erhalten', ownerKind: ItemOwner.STATION, ownerName: null,
        } as MovementResponse
        const piece: NonNullable<MyInventoryItem['movement']> = {
            id: 4, state: MovementState.OPEN, reachedStepLabel: 'Ersatz ausgegeben', currentStepActor: StepActor.MEMBER,
            ownerKind: ItemOwner.STATION, ownerName: null,
        }

        expect(badges(piece)).toEqual(badges(row))
        expect(badges(piece)).toEqual({step: 'Ersatz ausgegeben', turn: 'Wartet auf: Mitglied', state: null})
    })

    it('shows only how it ended once it is over', () => {
        expect(badges(standing({state: MovementState.DONE, reachedStepLabel: 'Erhalten', currentStepActor: null})))
            .toEqual({step: null, turn: null, state: 'Abgeschlossen'})
    })
})
