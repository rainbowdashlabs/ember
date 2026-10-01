/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {
    AccountInfo,
    MemberGroup,
    MemberIdentity,
    MemberWithName,
    SessionInfo,
    UserTag,
} from '@/api/generated/schema'

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

export function createMember(overrides: Partial<MemberWithName> = {}): MemberWithName {
    const id = nextId++
    return {
        id,
        stationId: 'test-station',
        accountId: id,
        name: `Member ${id}`,
        firstName: 'Member',
        lastName: String(id),
        nickname: null,
        email: `member${id}@example.com`,
        username: null,
        userType: 'MEMBER',
        profileComplete: true,
        formerAt: null,
        joinDate: '2020-01-01',
        identity: {
            stationUid: 'station-uid',
            memberUid: `member-uid-${id}`,
            name: `Member ${id}`,
            stationName: 'Test Station',
            nameColor: null,
            displayTag: null,
        },
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
        position: id,
        groupSetId: null,
        userTypes: [],
        ...overrides,
    }
}

/**
 * Somebody signed in at a station as a plain member with no permissions beyond signing in, on an
 * instance that can send mail. A test overrides the account and whatever else its case is about.
 */
export function createSessionInfo(
    overrides: Partial<Omit<SessionInfo, 'account'>> & {account?: Partial<AccountInfo>} = {},
): SessionInfo {
    const id = nextId++
    const {account, ...rest} = overrides
    return {
        account: {
            id,
            uid: `account-uid-${id}`,
            email: `account${id}@example.com`,
            username: null,
            firstName: 'Account',
            lastName: String(id),
            ...account,
        },
        stationId: 'test-station',
        member: null,
        permissions: ['LOGIN'],
        userType: 'MEMBER',
        instanceUserType: 'USER',
        managedMembers: [],
        groups: [],
        tags: [],
        roleIds: [],
        groupIds: [],
        tagIds: [],
        profileComplete: true,
        disabledModules: [],
        theme: {
            instanceDefaultTheme: 'ember',
            instanceDefaultFeel: 'ROUNDED',
            instanceLockFeel: false,
            defaultTheme: 'ember',
            defaultFeel: 'ROUNDED',
            allowUserTheme: true,
            allowUserFeel: true,
            customThemeColors: null,
            userTheme: null,
            userDarkMode: null,
            userFeel: null,
        },
        publicKbMode: null,
        setupCompletedAt: null,
        clusterId: null,
        clusterUserType: null,
        clusterPermissions: [],
        ownStationPermissions: [],
        canSendMail: true,
        pdfHidesInstanceUrl: false,
        stationTimezone: 'UTC',
        ...rest,
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
        position: id,
        ...overrides,
    }
}
