/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {MemberGroup, MemberIdentity, StationMember, UserTag} from '@/api/types'

/**
 * Typed test data with sensible defaults, so a test states only what it cares about and the rest
 * stays out of the way. Each factory takes an override object shaped like the type it builds.
 *
 * A factory for a feature's own type belongs to that feature's api module by the type co-location
 * rule; only the cross-feature shapes live here.
 */
let nextId = 1

export function resetFactories() {
    nextId = 1
}

export function createIdentity(overrides: Partial<MemberIdentity> = {}): MemberIdentity {
    const id = nextId++
    return {
        stationUid: 'station-uid',
        memberUid: `member-uid-${id}`,
        name: `Member ${id}`,
        stationName: 'Test Station',
        nameColor: null,
        displayTag: null,
        ...overrides,
    }
}

export function createMember(overrides: Partial<StationMember> = {}): StationMember {
    const id = nextId++
    return {
        id,
        stationId: 'test-station',
        accountId: id,
        name: `Member ${id}`,
        email: `member${id}@example.com`,
        userType: 'MEMBER',
        profileComplete: true,
        formerAt: null,
        identity: createIdentity({name: `Member ${id}`}),
        ...overrides,
    }
}

export function createGroup(overrides: Partial<MemberGroup> = {}): MemberGroup {
    const id = nextId++
    return {
        id,
        stationId: 'test-station',
        name: `Group ${id}`,
        color: null,
        ...overrides,
    }
}

export function createTag(overrides: Partial<UserTag> = {}): UserTag {
    const id = nextId++
    return {
        id,
        stationId: 'test-station',
        name: `Tag ${id}`,
        color: null,
        visible: true,
        ...overrides,
    }
}
