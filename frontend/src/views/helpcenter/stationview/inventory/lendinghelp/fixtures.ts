/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {AvailableInventoryEntry, LendingRequestResponse, LendingStatus} from '@/api/generated/schema'

/** What two partner stations offer, one with a known distance and one without. */
export const offers: AvailableInventoryEntry[] = [
  {
    artId: null,
    artName: null,
    availableCount: 5,
    distanceKm: 12.4,
    inventoryId: 1,
    inventoryName: 'Rettungsringe',
    stationId: 'musterstadt',
    stationName: 'DLRG Musterstadt',
  },
  {
    artId: null,
    artName: null,
    availableCount: 3,
    distanceKm: null,
    inventoryId: 2,
    inventoryName: 'Taucheranzüge',
    stationId: 'beispielburg',
    stationName: 'DLRG Beispielburg',
  },
]

interface RequestFixture {
  id: number
  partner: string
  from: string
  to: string
  items: string
  status: LendingStatus
  overdue?: boolean
}

/** A lending request as the list receives it, seen from this station's side of the exchange. */
function request(fixture: RequestFixture, isOwner: boolean): LendingRequestResponse {
  return {
    isOwner,
    itemSummary: fixture.items,
    overdue: fixture.overdue ?? false,
    owningStationName: isOwner ? 'DLRG Hafenstadt' : fixture.partner,
    requestingStationName: isOwner ? fixture.partner : 'DLRG Hafenstadt',
    request: {
      createdAt: '2026-01-15T10:00:00Z',
      createdBy: null,
      eventDate: null,
      eventId: null,
      id: fixture.id,
      occasion: '',
      owningStationUid: '',
      requestedDateFrom: fixture.from,
      requestedDateTo: fixture.to,
      requestingStationUid: '',
      status: fixture.status,
      uid: `help-${fixture.id}`,
      updatedAt: '2026-01-15T10:00:00Z',
    },
  }
}

/** Requests partners sent to this station: one waiting for an answer and one out on loan. */
export const incomingRequests: LendingRequestResponse[] = ([
  {id: 1, partner: 'DLRG Neustadt', from: '2026-06-01', to: '2026-06-15', items: 'Rettungsring (3x)', status: 'REQUESTED'},
  {id: 2, partner: 'DLRG Beispielburg', from: '2026-05-10', to: '2026-05-20', items: 'Taucheranzug (1x)', status: 'LENT', overdue: true},
] satisfies RequestFixture[]).map(fixture => request(fixture, true))

/** Requests this station sent, one in each of the remaining states. */
export const outgoingRequests: LendingRequestResponse[] = ([
  {id: 3, partner: 'DLRG Musterstadt', from: '2026-07-05', to: '2026-07-10', items: 'Erste-Hilfe-Koffer (2x)', status: 'APPROVED'},
  {id: 4, partner: 'DLRG Neustadt', from: '2026-04-01', to: '2026-04-05', items: 'Schwimmweste (1x)', status: 'DECLINED'},
  {id: 5, partner: 'DLRG Musterstadt', from: '2026-03-20', to: '2026-03-25', items: 'Schwimmflossen (4x)', status: 'RETURNED'},
  {id: 6, partner: 'DLRG Beispielburg', from: '2026-02-01', to: '2026-02-10', items: 'Taucherbrille (2x)', status: 'CLOSED'},
] satisfies RequestFixture[]).map(fixture => request(fixture, false))
