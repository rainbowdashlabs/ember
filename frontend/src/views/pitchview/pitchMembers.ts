/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {MemberIdentity, MemberWithName} from '@/api/generated/schema'

/**
 * Somebody of the demonstration station as the server identifies a member: no colour, no tag, and no
 * station name, because the station is the reader's own.
 */
export function pitchIdentity(name: string, memberUid: string): MemberIdentity {
    return {stationUid: 'wache', memberUid, name, stationName: null, nameColor: null, displayTag: null}
}

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
        identity: pitchIdentity(name, `m-${id}`),
    }
}
