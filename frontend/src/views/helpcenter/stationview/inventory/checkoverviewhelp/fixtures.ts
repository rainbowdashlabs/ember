/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {EnrichedCheckSummary} from '@/api/generated/schema'
import {StationUserType} from '@/api/types'

/** The reader of the help page, who holds the lock on one of the checks below. */
export const currentMemberId = 100

/** A member nobody is checking right now, last checked by the given person or never. */
function summary(fields: Pick<EnrichedCheckSummary, 'memberId' | 'firstName' | 'lastName'> & Partial<EnrichedCheckSummary>): EnrichedCheckSummary {
    return {
        checkerFirstName: null, checkerLastName: null, lastCheckedAt: null,
        locked: false, lockedBy: null, lockerFirstName: null, lockerLastName: null,
        userType: StationUserType.TEAM,
        identity: {memberUid: '', stationUid: '', name: `${fields.firstName} ${fields.lastName}`, nameColor: null, displayTag: null, stationName: null},
        ...fields,
    }
}

/** One checked member, one never checked, one the reader is checking and one somebody else is. */
export const members: EnrichedCheckSummary[] = [
    summary({memberId: 1, firstName: 'Max', lastName: 'Mustermann', lastCheckedAt: '2026-05-10T12:30:00Z', checkerFirstName: 'Anna', checkerLastName: 'Becker'}),
    summary({memberId: 2, firstName: 'Erika', lastName: 'Musterfrau'}),
    summary({
        memberId: 3, firstName: 'Jan', lastName: 'Schmidt', lastCheckedAt: '2026-05-12T07:00:00Z',
        checkerFirstName: 'Anna', checkerLastName: 'Becker', locked: true, lockedBy: currentMemberId,
    }),
    summary({
        memberId: 4, firstName: 'Lisa', lastName: 'Müller', lastCheckedAt: '2026-05-05T14:00:00Z',
        checkerFirstName: 'Tom', checkerLastName: 'Weber', locked: true, lockedBy: 5, lockerFirstName: 'Tom', lockerLastName: 'Weber',
    }),
]
