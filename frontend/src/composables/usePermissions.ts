/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {StationPermission, type StationPermissionName} from '@/api/types'
import {sessionInfo} from '@/util/sessionState'

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

function hasPermission(permission: string): boolean {
    return sessionInfo.value?.permissions?.includes(permission) ?? false
}

function hasAny(group: PermissionGroup): boolean {
    return PERMISSION_GROUPS[group].some(permission => hasPermission(permission))
}

function isAdmin(): boolean {
    return sessionInfo.value?.instanceUserType === 'ADMINISTRATOR'
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
        isAdmin,
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
