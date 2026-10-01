/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {MemberWithName} from '@/api/generated/schema'

/**
 * A member of the demonstration station, in the shape the station's member list sends, so the
 * application's own screens can draw them as they draw a real one.
 */
export function pitchMember(id: number, name: string): MemberWithName {
    const [firstName = name, ...rest] = name.split(' ')
    return {
        id,
        stationId: 'wache',
        accountId: id,
        name,
        firstName,
        lastName: rest.join(' '),
        nickname: null,
        email: null,
        username: null,
        userType: 'MEMBER',
        profileComplete: true,
        formerAt: null,
        joinDate: '2020-01-01',
        identity: {stationUid: 'wache', memberUid: `m-${id}`, name, stationName: null, nameColor: null, displayTag: null},
    }
}
