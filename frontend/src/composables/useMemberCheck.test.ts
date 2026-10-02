/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {mount} from '@vue/test-utils'
import {defineComponent, ref} from 'vue'
import {describe, expect, it} from 'vitest'
import {
    ItemCustody,
    ItemOwner,
    MovementState,
    StepActor,
    type InventoryItem,
    type MemberCheckState,
    type MovementStanding,
} from '@/api/generated/schema'
import {useMemberCheck} from './useMemberCheck'

function piece(id: number): InventoryItem {
    return {
        id, inventoryId: 1, name: 'Jacke', internalId: `INV-${id}`, sizeId: null, artId: null, assignedTo: 1,
        containerId: null, custody: ItemCustody.WITH_MEMBER, custodyMovementId: null, custodyPartnerStationId: null,
        custodyStationId: null, loanRequestItemId: null, lostAt: null, lostNote: null, lostNoteBy: null,
        metadata: {fields: {}}, ownerClusterId: null, ownerKind: ItemOwner.STATION, ownerStationId: null,
    }
}

function walk(onTheMove: Record<number, MovementStanding>): MemberCheckState {
    return {
        memberName: 'Max Mustermann',
        memberIdentity: {memberUid: '', stationUid: '', name: 'Max Mustermann', nameColor: null, displayTag: null, stationName: null},
        required: [],
        assigned: [piece(1), piece(2)],
        unassigned: {},
        onTheMove,
        overtookSelfChecks: [],
        lastCheck: null,
    }
}

function checkOf(state: MemberCheckState | null) {
    let result: ReturnType<typeof useMemberCheck> | null = null
    mount(defineComponent({
        setup() {
            result = useMemberCheck(ref(1), ref(state), ref(null))
            return () => null
        },
    }))
    return result as unknown as ReturnType<typeof useMemberCheck>
}

const exchangeAsked: MovementStanding = {
    id: 7, state: MovementState.OPEN, reachedStepLabel: 'Tausch angefordert', currentStepActor: StepActor.STATION,
    ownerKind: ItemOwner.STATION, ownerName: null,
}

describe('useMemberCheck movementOf', () => {
    it('hands on the movement standing the walk was told for a moving piece', () => {
        const check = checkOf(walk({1: exchangeAsked}))

        expect(check.movementOf(1)).toEqual(exchangeAsked)
    })

    it('names no movement for a piece nothing runs on', () => {
        const check = checkOf(walk({1: exchangeAsked}))

        expect(check.movementOf(2)).toBeNull()
    })

    it('names no movement before the walk has loaded', () => {
        expect(checkOf(null).movementOf(1)).toBeNull()
    })
})
