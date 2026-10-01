/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {MovementResponse} from '@/api/generated/schema'
import {ItemOwner} from '@/api/inventory'
import {MovementParty, MovementPurpose, MovementState, StepActor} from '@/api/movements'

/** An open movement of a station's own piece, with no turn taken yet beyond what the fields say. */
function movement(fields: Pick<MovementResponse, 'id' | 'purpose' | 'itemName' | 'inventoryName' | 'memberName'> & Partial<MovementResponse>): MovementResponse {
    return {
        actionable: false, belongsOnAnotherFlow: false, closedAt: null, closeReason: null, color: null,
        createdAt: '2026-05-01T08:00:00Z', currentStepActor: StepActor.STATION, currentStepCustody: null,
        currentStepLabel: null, currentStepSubject: null, icon: null, incomingItemId: null, incomingItemName: null,
        inventoryId: 1, inventoryType: null, itemId: null, itemInternalId: null, itemSizeName: null,
        itemStillWithMember: false, memberId: null, newSizeId: null, newSizeName: null, oldSizeId: null,
        oldSizeName: null, ownerAnswersHere: false, ownerClusterId: null, ownerKind: ItemOwner.STATION, ownerName: null,
        party: MovementParty.MEMBER, reachedStepLabel: null, reason: '', state: MovementState.OPEN,
        updatedAt: '2026-05-01T08:00:00Z',
        memberIdentity: {memberUid: '', stationUid: '', name: fields.memberName, nameColor: null, displayTag: null, stationName: null},
        ...fields,
    }
}

/** An exchange waiting on the station, a request waiting on the association and an issue on its way. */
export const movements: MovementResponse[] = [
    movement({
        id: 1, purpose: MovementPurpose.EXCHANGE, itemName: 'Helm', inventoryName: 'Helme', itemSizeName: 'M',
        icon: 'helmet-safety', color: '#2563eb', memberName: 'Max Mustermann', actionable: true,
        currentStepActor: StepActor.STATION, currentStepLabel: 'Teil zurücknehmen', reachedStepLabel: 'Tausch angefordert',
        createdAt: '2026-05-12T08:00:00Z', updatedAt: '2026-05-14T09:00:00Z',
    }),
    movement({
        id: 2, purpose: MovementPurpose.REQUEST, itemName: 'Einsatzjacke', inventoryName: 'Jacken', itemSizeName: 'S',
        icon: 'shirt', color: '#16a34a', memberName: 'Erika Musterfrau',
        currentStepActor: StepActor.OWNER, reachedStepLabel: 'Beim Verband angefragt',
        createdAt: '2026-05-10T08:00:00Z', updatedAt: '2026-05-10T08:00:00Z',
    }),
    movement({
        id: 3, purpose: MovementPurpose.ISSUE, itemName: 'Stiefel', inventoryName: 'Stiefel', itemSizeName: '44',
        icon: 'shoe-prints', color: '#ca8a04', memberName: 'Jan Schmidt',
        currentStepActor: StepActor.MEMBER, reachedStepLabel: 'Auf dem Weg zum Mitglied',
        createdAt: '2026-04-01T08:00:00Z', updatedAt: '2026-04-02T08:00:00Z',
    }),
]
