/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {StationPermission, type StationPermissionName} from '@/api/types'
import {sessionInfo} from '@/util/sessionState'
import {getActingStation} from '@/util/actingStationState'

/**
 * Declarative permission groups behind the named helpers below. A group is granted as soon
 * as the session carries any single permission listed in it, so a one-entry group expresses
 * a plain "may manage X" check and a longer group expresses "has any X permission at all".
 */
const PERMISSION_GROUPS = {
    stationManagement: [
        StationPermission.STATION_ADMINISTRATOR,
        StationPermission.STATION_GENERAL,
        StationPermission.STATION_LOOK_AND_FEEL,
        StationPermission.STATION_MAIL,
        StationPermission.STATION_MODULES,
        StationPermission.STATION_IMPORT_EXPORT,
        StationPermission.STATION_STATISTICS,
    ],
    memberManagement: [StationPermission.MEMBER_MANAGER],
    memberAccess: [
        StationPermission.MEMBER_MANAGER,
        StationPermission.MEMBER_READ,
        StationPermission.MEMBER_EDIT,
        StationPermission.MEMBER_NOTES,
        StationPermission.MEMBER_CHANGES,
        StationPermission.MEMBER_MANAGE_GROUP,
        StationPermission.MEMBER_MANAGE_TAGS,
        StationPermission.MEMBER_FIELDS,
    ],
    guardian: [StationPermission.MEMBER_GUARDIAN],
    inventoryManagement: [StationPermission.INVENTORY_MANAGER],
    exchangeManagement: [StationPermission.INVENTORY_EXCHANGE],
    attendanceManagement: [StationPermission.ATTENDANCE_MANAGER],
    attendanceAccess: [
        StationPermission.ATTENDANCE_MANAGER,
        StationPermission.ATTENDANCE_READ,
        StationPermission.ATTENDANCE_EDIT,
        StationPermission.ATTENDANCE_CONFIGURE,
        StationPermission.ATTENDANCE_EXPORT,
    ],
    attendanceExport: [StationPermission.ATTENDANCE_EXPORT],
    eventManagement: [StationPermission.EVENT_MANAGER],
    newsManagement: [StationPermission.NEWS_MANAGER],
    waitlistAccess: [
        StationPermission.WAITLIST_MANAGER,
        StationPermission.WAITLIST_READ,
        StationPermission.WAITLIST_ADD,
        StationPermission.WAITLIST_EDIT,
    ],
    knowledgeEditing: [StationPermission.KNOWLEDGE_EDIT],
    protocolManagement: [StationPermission.PROTOCOL_MANAGER],
    protocolTesting: [StationPermission.PROTOCOL_TESTER],
    boardManagement: [StationPermission.BOARD_MANAGER],
    federationManagement: [StationPermission.STATION_FEDERATION],
} as const satisfies Record<string, readonly StationPermissionName[]>

type PermissionGroup = keyof typeof PERMISSION_GROUPS

/**
 * Whether the caller may do this at the station the request is for.
 *
 * <p>Usually that is the station they belong to. While a screen is open on the association's side, it is the
 * station the association owns, where its knowledge base, news and calendar are kept: those screens are the
 * station's own and ask the station's own question, and the answer there comes from what the reader holds at
 * the association rather than from any membership, which they do not have.
 */
function hasPermission(permission: string): boolean {
    const held = getActingStation()
        ? sessionInfo.value?.ownStationPermissions
        : sessionInfo.value?.permissions
    return held?.includes(permission) ?? false
}

/**
 * Whether the caller holds a permission at the cluster they are acting for. Separate from the station check
 * because the two sets are separate: one person can hold everything at a cluster and nothing at its stations.
 */
function hasClusterPermission(permission: string): boolean {
    return sessionInfo.value?.clusterPermissions?.includes(permission) ?? false
}

function hasAny(group: PermissionGroup): boolean {
    return PERMISSION_GROUPS[group].some(permission => hasPermission(permission))
}

function isAdmin(): boolean {
    return sessionInfo.value?.instanceUserType === 'ADMINISTRATOR'
}

/**
 * Whether the caller may edit the account behind a name on a station's roll.
 *
 * <p>The station right is the ordinary way in. Whoever administers the instance is let in beside it,
 * because of the one account nobody else can help: an administrator whose address cannot be written
 * to cannot correct it themselves, since the confirmation would go to the address being corrected,
 * and the person who can do it for them need not be at their station.
 */
function canEditMemberAccounts(): boolean {
    return hasPermission(StationPermission.MEMBER_EDIT) || isAdmin()
}

function isModuleEnabled(module: string): boolean {
    return !(sessionInfo.value?.disabledModules?.includes(module) ?? false)
}

/**
 * Permission checks for the signed-in session. Every named helper is a thin read over
 * {@link PERMISSION_GROUPS}; the names are the public API and are consumed through
 * {@code useSession}, which composes them in.
 */
export function usePermissions() {
    return {
        hasPermission,
        hasClusterPermission,
        isAdmin,
        canEditMemberAccounts,
        isModuleEnabled,
        isManager: () => hasAny('stationManagement'),
        canManageMembers: () => hasAny('memberManagement'),
        hasAnyMemberPermission: () => hasAny('memberAccess'),
        isGuardian: () => hasAny('guardian'),
        canManageInventory: () => hasAny('inventoryManagement'),
        canManageExchanges: () => hasAny('exchangeManagement'),
        canManageAttendance: () => hasAny('attendanceManagement'),
        hasAnyAttendancePermission: () => hasAny('attendanceAccess'),
        canExportAttendance: () => hasAny('attendanceExport'),
        canManageEvents: () => hasAny('eventManagement'),
        canManageNews: () => hasAny('newsManagement'),
        hasAnyWaitlistPermission: () => hasAny('waitlistAccess'),
        canEditKnowledge: () => hasAny('knowledgeEditing'),
        canManageProtocol: () => hasAny('protocolManagement'),
        canTestProtocol: () => hasAny('protocolTesting'),
        canManageBoards: () => hasAny('boardManagement'),
        canManageFederation: () => hasAny('federationManagement'),
    }
}
