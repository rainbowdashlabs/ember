/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {EnrichedCheckDetail} from '@/api/generated/schema'

/** The last check of a member, with one piece in each of the three outcomes. */
export const detail: EnrichedCheckDetail = {
    check: {
        id: 1, checkedAt: '2026-05-10T12:30:00Z', checkedBy: 2, containerId: null, deep: false,
        memberId: 1, reportedBy: null, scope: 'MEMBER', stationId: '',
    },
    checkerFirstName: 'Anna',
    checkerLastName: 'Becker',
    reporterFirstName: '',
    reporterLastName: '',
    items: [
        {id: 1, itemId: 1, itemName: 'Helm', sizeName: 'M', inventoryName: 'Helme', internalId: 'INV-0001', note: '', result: 'CONFIRMED'},
        {id: 2, itemId: 2, itemName: 'Jacke', sizeName: 'L', inventoryName: 'Jacken', internalId: 'INV-0015', note: 'Konnte nicht gefunden werden', result: 'LOST'},
        {id: 3, itemId: 3, itemName: 'Stiefel', sizeName: '42', inventoryName: 'Stiefel', internalId: 'INV-0030', note: '', result: 'NOT_IN_POSSESSION'},
    ],
}
