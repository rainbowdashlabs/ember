/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// -- Roles --

export type RoleName =
    | 'LOGIN'
    | 'USER'
    | 'MEMBER'
    | 'MEMBER_MANAGER'
    | 'TEAM'
    | 'ATTENDENCE_MANAGEMENT'
    | 'ATTENDENCE_EXPORT_MANAGER'
    | 'INVENTORY_MANAGEMENT'
    | 'EVENT_MANAGEMENT'
    | 'MEMBER_MANAGEMENT'
    | 'NEWS_MANAGEMENT'
    | 'MANAGER'
    | 'ADMIN'

// -- Common --

export interface ErrorResponseWrapper {
    error?: string
    message?: string
}

export interface MessageResponse {
    message?: string
}

// -- Auth --

export interface LoginRequest {
    email?: string
    password?: string
}

export interface LoginResponse {
    token?: string
    expiresAt?: string
    passwordChangeRequired: boolean
    passwordChangeToken?: string
    passwordChangeTokenExpiresAt?: string
}

export interface RegisterRequest {
    email?: string
    firstName?: string
    lastName?: string
    password?: string
    registrationCode?: string
}

export interface RegisterResponse {
    id: number
    email?: string
    firstName?: string
    lastName?: string
    emailVerified: boolean
}

export interface TokenRequest {
    token?: string
}

export interface EmailRequest {
    email?: string
}

export interface SetPasswordRequest {
    token?: string
    password?: string
}

export interface SessionResponse {
    token?: string
    expiresAt?: string
}

// -- Session --

export interface AccountInfo {
    id: number
    email?: string
    firstName?: string
    lastName?: string
}

export interface MemberInfo {
    id: number
    stationId: number
    accountId: number
}

export interface SessionInfo {
    account?: AccountInfo
    stationId?: number
    member?: MemberInfo
    roles?: string[]
    managedMembers?: StationMember[]
    groups?: MemberGroup[]
}

export interface ActiveSession {
    id: number
    userAgent?: string
    createdAt?: string
    lastUsedAt?: string
    expiresAt?: string
}

export interface StationMembership {
    memberId: number
    stationId: number
    stationName?: string
}

// -- Events --

export interface EventCategory {
    id: number
    stationId: number
    name?: string
    position: number
}

export interface CategoryRequest {
    name?: string
    position: number
}

export interface StationEvent {
    id: number
    stationId: number
    name?: string
    description?: string
    eventType?: string
    dayOfWeek?: number | null
    startTime?: string
    endTime?: string
    templateId?: number | null
    requiresRegistration?: boolean
    registrationDeadline?: string | null
    requiresConfirmation?: boolean
    categoryId?: number | null
}

export interface EventRequest {
    name?: string
    description?: string
    eventType?: string
    dayOfWeek?: number | null
    startTime?: string
    endTime?: string
    templateId?: number | null
    requiresRegistration?: boolean
    registrationDeadline?: string | null
    requiresConfirmation?: boolean
    categoryId?: number | null
    restrictedRoleIds?: number[]
    restrictedGroupIds?: number[]
}

export interface EventRestrictions {
    roleIds: number[]
    groupIds: number[]
}

export interface AllEventRestrictions {
    roleRestrictions: Record<number, number[]>
    groupRestrictions: Record<number, number[]>
}

export interface EventBreak {
    id: number
    stationId: number
    name?: string
    startDate?: string
    endDate?: string
}

export interface BreakRequest {
    name?: string
    startDate?: string
    endDate?: string
}

// -- Station Manage --

export interface StationManageInfo {
    id: number
    name?: string
    timezone?: string
    locale?: string
    hasLogo: boolean
}

export interface UpdateStationNameRequest {
    name?: string
    timezone?: string
    locale?: string
}

// -- Stations --

export interface Station {
    id: number
    name?: string
}

export interface StationRequest {
    name?: string
    managerEmail?: string
}

export interface StationDetail {
    id: number
    name?: string
    manager?: ManagerDetail | null
}

export interface ManagerDetail {
    email?: string
    firstName?: string
    lastName?: string
    accountReady: boolean
}

// -- Station Members --

export interface StationMember {
    id: number
    stationId: number
    accountId: number
    name?: string
    email?: string
}

export interface CreateMemberRequest {
    stationId?: number
    accountId?: number
}

export interface Role {
    id: number
    role: RoleName
}

export interface SetRolesRequest {
    roleIds?: number[]
}

export interface SetManagersRequest {
    managerIds?: number[]
}

// -- Members --

export interface InviteRequest {
    email?: string
    firstName?: string
    lastName?: string
}

export interface InviteResponse {
    id: number
    email?: string
    firstName?: string
    lastName?: string
}

export interface ResetPasswordRequest {
    accountId?: number
    forceChange?: boolean
}

// -- Attendance --

export interface AttendanceTemplate {
    id: number
    stationId: number
    name?: string
}

export interface TemplateRequest {
    name?: string
}

export interface AttendanceTemplateField {
    id: number
    templateId: number
    name?: string
    fieldType?: string
    config?: string
    position: number
}

export interface TemplateFieldRequest {
    name?: string
    fieldType?: string
    config?: string
    position: number
}

export interface TemplateDetail {
    id: number
    stationId: number
    name?: string
    fields?: AttendanceTemplateField[]
    groups?: TemplateGroupEntry[]
}

export interface TemplateGroupEntry {
    groupId: number
    position: number
}

export interface SetTemplateGroupsRequest {
    groups?: TemplateGroupEntry[]
}

export interface AttendanceSession {
    id: number
    templateId: number
    startTime?: string
    endTime?: string
    createdAt?: string
    eventId?: number | null
    title?: string
}

export interface SessionRequest {
    startTime?: string
    endTime?: string
    eventId?: number | null
    title?: string
}

export interface AttendanceSessionField {
    sessionId: number
    fieldId: number
    value?: string
}

export interface AttendanceFieldValueEntry {
    fieldId: number
    value?: string
}

export interface SetSessionFieldsRequest {
    fields?: AttendanceFieldValueEntry[]
}

export interface SessionDetail {
    session?: AttendanceSession
    fields?: AttendanceSessionField[]
    entries?: AttendanceEntry[]
}

export type AttendanceStatus = 'UNCONFIRMED' | 'PRESENT' | 'ABSENT' | 'DECLINED'

export type EntrySource = 'EXPECTED' | 'EXTRA'

export interface AttendanceEntry {
    id: number
    sessionId: number
    memberId: number
    status: AttendanceStatus
    checkIn?: string
    checkOut?: string
    source: EntrySource
}

export interface CreateEntryRequest {
    memberId?: number
    source?: EntrySource
}

export interface TimestampRequest {
    time?: string
}

export interface TimestampResponse {
    entryId: number
    time?: string
}

// -- Inventory --

export interface Inventory {
    id: number
    stationId: number
    name?: string
    inventoryType?: string
    hasSizes: boolean
}

export interface InventoryRequest {
    name?: string
    inventoryType?: string
    hasSizes: boolean
}

export interface InventoryDetail {
    id: number
    stationId: number
    name?: string
    inventoryType?: string
    hasSizes: boolean
    sizes?: InventorySize[]
}

export interface InventorySize {
    id: number
    inventoryId: number
    label?: string
    position: number
    note?: string
}

export interface SizeRequest {
    label?: string
    position: number
    note?: string
}

export interface InventoryItem {
    id: number
    inventoryId: number
    internalId?: string
    name?: string
    sizeId?: number | null
    metadata?: string
    assignedTo?: number | null
    lostAt?: string | null
}

export interface ItemRequest {
    internalId?: string
    name?: string
    sizeId?: number
    metadata?: string
}

export interface AssignRequest {
    memberId?: number | null
    memberName?: string
}

export interface InventoryRequirement {
    id: number
    inventoryId: number
    roleId: number
    groupId: number
    quantity: number
    position: number
}

export interface RequirementRequest {
    inventoryId: number
    roleId?: number
    groupId?: number
    quantity?: number
}

export interface InventoryItemHistory {
    id: number
    itemId: number
    memberId?: number | null
    memberName?: string
    givenOut?: string
    returned?: string | null
}

// -- Inventory Checks --

export interface MemberCheckSummary {
    memberId: number
    firstName?: string
    lastName?: string
    lastCheckedAt?: string | null
    checkerFirstName?: string | null
    checkerLastName?: string | null
    locked: boolean
    lockedBy?: number | null
    lockerFirstName?: string | null
    lockerLastName?: string | null
    roles: string[]
}

export interface CheckDetail {
    check: InventoryCheck
    checkerFirstName?: string
    checkerLastName?: string
    items: EnrichedCheckItem[]
}

export interface EnrichedCheckItem {
    id: number
    itemId?: number | null
    itemName?: string | null
    internalId?: string | null
    inventoryName: string
    sizeName?: string | null
    result: CheckResult
    note: string
}

export interface InventoryCheckItem {
    id: number
    checkId: number
    itemId: number
    result: CheckResult
    note: string
}

export interface MemberCheckState {
    memberName: string
    required: RequiredInventoryItem[]
    assigned: InventoryItem[]
    lastCheck?: InventoryCheck | null
    unassigned: Record<number, InventoryItem[]>
}

export interface RequiredInventoryItem {
    inventoryId: number
    inventoryName: string
    inventoryType: string
    hasSizes: boolean
    sizes: InventorySize[]
    requiredQuantity: number
    assignedQuantity: number
}

export interface InventoryCheck {
    id: number
    stationId: number
    memberId: number
    checkedBy: number
    checkedAt: string
}

export type CheckResult = 'CONFIRMED' | 'NOT_IN_POSSESSION' | 'LOST'

export interface CheckItemResult {
    itemId?: number | null
    inventoryId?: number | null
    result: CheckResult
    note?: string
}

export interface CompleteCheckRequest {
    items: CheckItemResult[]
}

export interface NextMemberResponse {
    memberId: number | null
}

// -- Profile Fields --

export interface ProfileField {
    id: number
    stationId: number
    name?: string
    fieldType?: string
    config?: string
    position: number
    scope?: string
}

export interface ProfileFieldRequest {
    name?: string
    fieldType?: string
    config?: string
    position: number
    scope?: string
}

export interface ProfileFieldValue {
    memberId: number
    fieldId: number
    value?: string
}

export interface ProfileFieldValueEntry {
    fieldId: number
    value?: string
}

export interface SetValuesRequest {
    values?: ProfileFieldValueEntry[]
}

// -- Profile Field Changes --

export interface ProfileFieldChangeAcknowledgement {
    id: number
    changeId: number
    acknowledgedBy: number
    acknowledgedAt?: string
    comment?: string
    acknowledgedByName?: string
}

export interface ProfileFieldChange {
    id: number
    fieldId: number
    memberId: number
    oldValue?: string
    newValue?: string
    changedBy: number
    changedAt?: string
    requiresAcknowledgement: boolean
    changedByName?: string
    fieldName?: string
    acknowledgements: ProfileFieldChangeAcknowledgement[]
}

export interface MemberChangeSummary {
    memberId: number
    memberName?: string
    pendingCount: number
    latestChange?: string
}

export interface AcknowledgeRequest {
    comment?: string
}

// -- Member Groups --

export interface MemberGroup {
    id: number
    stationId: number
    name?: string
}

export interface GroupRequest {
    name?: string
}

export interface GroupDetail {
    id: number
    stationId: number
    name?: string
    members?: StationMember[]
}

export interface SetMembersRequest {
    memberIds?: number[]
}

// -- Registration Codes --

export interface RegistrationCode {
    id: number
    stationId: number
    code?: string
    maxUses: number
    uses: number
    hasUsesLeft: boolean
}

export interface CreateCodeRequest {
    code?: string
    maxUses: number
}

export interface CodeDetail {
    id: number
    stationId: number
    code?: string
    maxUses: number
    uses: number
    groupIds?: number[]
}

export interface SetGroupsRequest {
    groupIds?: number[]
}

// -- News --

export interface NewsEntry {
    id: number
    stationId: number
    title: string
    contentMarkdown: string
    contentHtml: string
    authorId: number
    authorName: string
    publishedAt?: string
    createdAt?: string
    groupIds: number[]
    commentCount: number
}

export interface NewsRequest {
    title: string
    contentMarkdown: string
    contentHtml: string
    groupIds: number[]
}

export interface NewsComment {
    id: number
    newsId: number
    parentId: number | null
    authorId: number
    authorName: string
    content: string
    createdAt: string
}

export interface CommentRequest {
    parentId?: number | null
    content: string
}

// -- User Settings --

export interface UserSettings {
    memberId: number
    notifyNews: boolean
    notifyNewEvents: boolean
    notifyEventStatus: boolean
}

export interface UserSettingsRequest {
    notifyNews: boolean
    notifyNewEvents: boolean
    notifyEventStatus: boolean
}

// -- Equipment Exchange --

export type ExchangeStatus = 'ANNOUNCED' | 'RECEIVED' | 'SHIPPED' | 'ARRIVED' | 'EXCHANGED'

export interface ExchangeRequestEntry {
    id: number
    memberId: number
    memberName: string
    itemId?: number | null
    inventoryId: number
    inventoryName: string
    sizeId?: number | null
    sizeLabel: string
    inventoryType: string
    status: ExchangeStatus
    reason: string
    createdAt: string
    updatedAt: string
}

export interface ExchangeLogEntry {
    id: number
    oldStatus: string
    newStatus: string
    changedBy: number
    changedByName: string
    changedAt: string
    note: string
}

export interface CreateExchangeRequest {
    itemId?: number | null
    inventoryId: number
    sizeId?: number | null
    reason: string
}

export interface UpdateStatusRequest {
    status: string
    note?: string
}

// -- Equipment Procurement --

export interface ProcurementEntry {
    id: number
    inventoryId: number
    inventoryName: string
    memberId: number
    memberName: string
    sizeId?: number | null
    sizeLabel: string
    notes: string
    requestedAt: string
    fulfilledAt?: string | null
}

export interface CreateProcurementRequest {
    inventoryId: number
    memberId: number
    sizeId?: number | null
    notes?: string
}

// -- User Tags --

export interface UserTag {
    id: number
    stationId: number
    name: string
}

// -- Notifications --

export type NotificationType = 'NEW_NEWS' | 'EVENT_REGISTRATION_STATUS' | 'EXCHANGE_STATUS_CHANGE'
    | 'EXCHANGE_NEW_REQUEST' | 'NEW_EVENT' | 'MEMBER_ADDED_TO_GROUP' | 'PROFILE_FIELD_CHANGED'

export interface NotificationEntry {
    id: number
    type: NotificationType
    referenceId?: number | null
    message: string
    createdAt: string
    acknowledgedAt?: string | null
}
