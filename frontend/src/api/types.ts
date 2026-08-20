/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
export const StationUserType = {
    TRIAL: 'TRIAL',
    MEMBER: 'MEMBER',
    GUARDIAN: 'GUARDIAN',
    TEAM: 'TEAM',
    MANAGER: 'MANAGER',
} as const

export type StationUserTypeName = (typeof StationUserType)[keyof typeof StationUserType]

/**
 * Human-readable German labels for each station user type. Single source of truth
 * for any picker, badge, filter, or summary that needs to render a user type name
 * without going through i18n.
 */
export const StationUserTypeLabels: Record<StationUserTypeName, string> = {
    TRIAL: 'Probe',
    MEMBER: 'Mitglied',
    GUARDIAN: 'Erziehungsberechtigter',
    TEAM: 'Team',
    MANAGER: 'Manager',
}

export const StationPermission = {
    LOGIN: 'LOGIN',
    USER: 'USER',
    ATTENDANCE_READ: 'ATTENDANCE_READ',
    ATTENDANCE_EDIT: 'ATTENDANCE_EDIT',
    ATTENDANCE_CONFIGURE: 'ATTENDANCE_CONFIGURE',
    ATTENDANCE_EXPORT: 'ATTENDANCE_EXPORT',
    ATTENDANCE_MANAGER: 'ATTENDANCE_MANAGER',
    INVENTORY_CREATE_EXTERNAL: 'INVENTORY_CREATE_EXTERNAL',
    INVENTORY_CREATE_INTERNAL: 'INVENTORY_CREATE_INTERNAL',
    INVENTORY_CREATE: 'INVENTORY_CREATE',
    INVENTORY_READ: 'INVENTORY_READ',
    INVENTORY_EDIT: 'INVENTORY_EDIT',
    INVENTORY_PROCUREMENT: 'INVENTORY_PROCUREMENT',
    INVENTORY_EXCHANGE: 'INVENTORY_EXCHANGE',
    INVENTORY_CHECK: 'INVENTORY_CHECK',
    INVENTORY_LENDING_REQUEST: 'INVENTORY_LENDING_REQUEST',
    INVENTORY_LENDING_MANAGER: 'INVENTORY_LENDING_MANAGER',
    INVENTORY_ASSIGN: 'INVENTORY_ASSIGN',
    INVENTORY_STORAGE: 'INVENTORY_STORAGE',
    INVENTORY_MANAGER: 'INVENTORY_MANAGER',
    EVENT_MANAGE_TEMPLATE: 'EVENT_MANAGE_TEMPLATE',
    EVENT_MANAGE_CATEGORY: 'EVENT_MANAGE_CATEGORY',
    EVENT_EDIT: 'EVENT_EDIT',
    EVENT_REGISTRATION: 'EVENT_REGISTRATION',
    EVENTS_FEDERATE: 'EVENTS_FEDERATE',
    EVENT_MANAGER: 'EVENT_MANAGER',
    MEMBER_READ: 'MEMBER_READ',
    MEMBER_NOTES: 'MEMBER_NOTES',
    MEMBER_GUARDIAN: 'MEMBER_GUARDIAN',
    MEMBER_CHANGES: 'MEMBER_CHANGES',
    MEMBER_MANAGE_GROUP: 'MEMBER_MANAGE_GROUP',
    MEMBER_MANAGE_TAGS: 'MEMBER_MANAGE_TAGS',
    MEMBER_EDIT: 'MEMBER_EDIT',
    MEMBER_FIELDS: 'MEMBER_FIELDS',
    MEMBER_EXPORT: 'MEMBER_EXPORT',
    MEMBER_MANAGER: 'MEMBER_MANAGER',
    WAITLIST_READ: 'WAITLIST_READ',
    WAITLIST_ADD: 'WAITLIST_ADD',
    WAITLIST_EDIT: 'WAITLIST_EDIT',
    WAITLIST_MANAGER: 'WAITLIST_MANAGER',
    NEWS_EDIT: 'NEWS_EDIT',
    NEWS_FEDERATE: 'NEWS_FEDERATE',
    NEWS_MANAGER: 'NEWS_MANAGER',
    POLL_VIEW_RESULTS: 'POLL_VIEW_RESULTS',
    POLL_CREATE: 'POLL_CREATE',
    POLL_MANAGER: 'POLL_MANAGER',
    LOST_AND_FOUND_CREATE: 'LOST_AND_FOUND_CREATE',
    LOST_AND_FOUND_MANAGE: 'LOST_AND_FOUND_MANAGE',
    LOST_AND_FOUND_MANAGER: 'LOST_AND_FOUND_MANAGER',
    CHECKLIST_READ: 'CHECKLIST_READ',
    CHECKLIST_MANAGE: 'CHECKLIST_MANAGE',
    CHECKLIST_MANAGER: 'CHECKLIST_MANAGER',
    TEST_CATALOG_VIEW: 'TEST_CATALOG_VIEW',
    TEST_CATALOG_EDIT: 'TEST_CATALOG_EDIT',
    TEST_CONFIGURE: 'TEST_CONFIGURE',
    TEST_RESULT_READ: 'TEST_RESULT_READ',
    TEST_REVIEW: 'TEST_REVIEW',
    TEST_MANAGER: 'TEST_MANAGER',
    PROTOCOL_TESTER: 'PROTOCOL_TESTER',
    PROTOCOL_CREATE: 'PROTOCOL_CREATE',
    PROTOCOL_CONFIGURE: 'PROTOCOL_CONFIGURE',
    PROTOCOL_MANAGER: 'PROTOCOL_MANAGER',
    BOARD_USE: 'BOARD_USE',
    BOARD_EDIT: 'BOARD_EDIT',
    BOARD_FEDERATE: 'BOARD_FEDERATE',
    BOARD_MANAGER: 'BOARD_MANAGER',
    PROCEDURE_READ: 'PROCEDURE_READ',
    PROCEDURE_EDIT: 'PROCEDURE_EDIT',
    PROCEDURE_MANAGER: 'PROCEDURE_MANAGER',
    PAGE_EDIT: 'PAGE_EDIT',
    PAGE_FORMS_VIEW: 'PAGE_FORMS_VIEW',
    PAGE_POLLS_VIEW: 'PAGE_POLLS_VIEW',
    PAGE_MANAGER: 'PAGE_MANAGER',
    KNOWLEDGE_EDIT: 'KNOWLEDGE_EDIT',
    KNOWLEDGE_FEDERATE: 'KNOWLEDGE_FEDERATE',
    KNOWLEDGE_MANAGER: 'KNOWLEDGE_MANAGER',
    STATION_LOOK_AND_FEEL: 'STATION_LOOK_AND_FEEL',
    STATION_GENERAL: 'STATION_GENERAL',
    STATION_MAIL: 'STATION_MAIL',
    STATION_FEDERATION: 'STATION_FEDERATION',
    STATION_MODULES: 'STATION_MODULES',
    STATION_IMPORT_EXPORT: 'STATION_IMPORT_EXPORT',
    STATION_STATISTICS: 'STATION_STATISTICS',
    STATION_MANAGER: 'STATION_MANAGER',
    STATION_ADMINISTRATOR: 'STATION_ADMINISTRATOR',
} as const

export type StationPermissionName = (typeof StationPermission)[keyof typeof StationPermission]

export interface ErrorResponseWrapper {
    error?: string
    message?: string
}

export interface MessageResponse {
    message?: string
}

/** Slice metadata every offset-paginated response carries alongside its rows. */
export interface PageMeta {
    total: number
    offset: number
    limit: number
}

/** Standard pagination envelope: one slice of rows plus the metadata describing it. */
export interface Page<T> extends PageMeta {
    items: T[]
}

export interface AccountInfo {
    id: number
    uid?: string
    email?: string
    firstName?: string
    lastName?: string
}

export interface MemberInfo {
    id: number
    stationId: string
    accountId: number
    uid: string
}

export interface ManagedMemberSummary {
    id: number
    stationId: string
    uid?: string | null
    accountId: number
    name?: string
    email?: string
}

export interface SessionInfo {
    account?: AccountInfo
    stationId?: string
    member?: MemberInfo
    permissions?: string[]
    userType?: string
    instanceUserType?: string
    managedMembers?: ManagedMemberSummary[]
    groups?: MemberGroup[]
    tags?: UserTag[]
    groupIds?: number[]
    tagIds?: number[]
    profileComplete?: boolean
    disabledModules?: string[]
    theme?: ThemeSessionInfo
    publicKbMode?: string
    /** ISO timestamp at which the station setup wizard was finished, or null while it still applies. */
    setupCompletedAt?: string | null
    /** The cluster this request is acting for, when the header named one. */
    clusterId?: string | null
    clusterUserType?: string | null
    /** What the caller may do at that cluster, which is a separate set from the station's. */
    clusterPermissions?: string[]
}

export interface ThemeSessionInfo {
    instanceDefaultTheme?: string
    instanceDefaultFeel?: string
    instanceLockFeel?: boolean
    defaultTheme?: string
    defaultFeel?: string
    allowUserTheme?: boolean
    allowUserFeel?: boolean
    customThemeColors?: string | null
    userTheme?: string
    userDarkMode?: string
    userFeel?: string
}

export const StationModules = {
    INVENTORY: 'INVENTORY',
    NEWS: 'NEWS',
    EVENTS: 'EVENTS',
    ATTENDANCE: 'ATTENDANCE',
    FORMS: 'FORMS',
    LOST_AND_FOUND: 'LOST_AND_FOUND',
    WAITING_LIST: 'WAITING_LIST',
    QUIZ: 'QUIZ',
    KNOWLEDGE_BASE: 'KNOWLEDGE_BASE',
    TEST_PROTOCOL: 'TEST_PROTOCOL',
    BOARDS: 'BOARDS',
    PROCEDURES: 'PROCEDURES',
} as const

export type StationModuleName = (typeof StationModules)[keyof typeof StationModules]

/**
 * Audience selection shared by every restriction-capable feature. The restriction
 * editor speaks this shape; each feature maps it onto its own request payload.
 */
export interface RestrictionSelection {
    userTypes: string[]
    groupIds: number[]
    tagIds: number[]
    memberIds: number[]
    mode: 'AND' | 'OR'
}

export interface StationMember {
    id: number
    stationId: string
    accountId: number
    name?: string
    email?: string
    userType?: string
    profileComplete?: boolean
    formerAt?: string | null
    identity?: MemberIdentity | null
    accountSetupPending?: boolean
    setupMailExpiresAt?: string | null
    /** ISO yyyy-MM-dd date when the member joined the station. */
    joinDate?: string | null
}

export interface PermissionGrant {
    id: number
    permission: string
}

export interface MemberGroup {
    id: number
    stationId: string
    name?: string
    color?: string | null
    position?: number
}

export interface UserTag {
    id: number
    stationId: string
    name: string
    color?: string | null
    visible?: boolean
    position?: number
}

export interface MemberIdentity {
    stationUid?: string
    memberUid?: string
    accountUid?: string
    name?: string | null
    stationName?: string | null
    nameColor?: string | null
    displayTag?: { name: string; color: string } | null
}
