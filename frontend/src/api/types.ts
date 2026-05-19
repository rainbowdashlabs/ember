/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// -- Roles --

export const Roles = {
    LOGIN: 'LOGIN',
    USER: 'USER',
    MEMBER: 'MEMBER',
    GUARDIAN: 'GUARDIAN',
    TEAM: 'TEAM',
    ATTENDENCE_MANAGEMENT: 'ATTENDENCE_MANAGEMENT',
    ATTENDENCE_EXPORT_MANAGER: 'ATTENDENCE_EXPORT_MANAGER',
    INVENTORY_MANAGEMENT: 'INVENTORY_MANAGEMENT',
    EVENT_MANAGEMENT: 'EVENT_MANAGEMENT',
    MEMBER_MANAGEMENT: 'MEMBER_MANAGEMENT',
    NEWS_MANAGEMENT: 'NEWS_MANAGEMENT',
    POLL_MANAGEMENT: 'POLL_MANAGEMENT',
    LOST_AND_FOUND_MANAGEMENT: 'LOST_AND_FOUND_MANAGEMENT',
    MANAGER: 'MANAGER',
    ADMIN: 'ADMIN',
} as const

export type RoleName = (typeof Roles)[keyof typeof Roles]

export const TEAM_ROLES: readonly RoleName[] = [
    Roles.TEAM, Roles.MANAGER, Roles.ADMIN,
    Roles.ATTENDENCE_MANAGEMENT, Roles.INVENTORY_MANAGEMENT,
    Roles.EVENT_MANAGEMENT, Roles.MEMBER_MANAGEMENT,
    Roles.POLL_MANAGEMENT,
] as const

export function isTeamRole(role: string): boolean {
    return (TEAM_ROLES as readonly string[]).includes(role)
}

export function hasTeamRole(roles: string[]): boolean {
    return roles.some(r => isTeamRole(r))
}

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
    profileComplete?: boolean
    disabledModules?: string[]
}

export const StationModules = {
    INVENTORY: 'INVENTORY',
    NEWS: 'NEWS',
    EVENTS: 'EVENTS',
    ATTENDANCE: 'ATTENDANCE',
    FORMS: 'FORMS',
    LOST_AND_FOUND: 'LOST_AND_FOUND',
} as const

export type StationModuleName = (typeof StationModules)[keyof typeof StationModules]

export interface ActiveSession {
    id: number
    userAgent?: string
    createdAt?: string
    lastUsedAt?: string
    expiresAt?: string
    isCurrent?: boolean
    location?: string
}

export interface DocumentResponse {
    html: string
    version: string
}

export interface LegalVersionsResponse {
    privacyVersion: string
    tosVersion: string
    consentVersion: string
}

export interface ConsentStatusResponse {
    consented: boolean
    current: boolean
    consentVersion?: string
    privacyVersion?: string
    tosVersion?: string
    consentedAt?: string
    currentPrivacyVersion: string
    currentTosVersion: string
    currentConsentVersion: string
}

export interface RecordConsentRequest {
    consentVersion: string
    privacyVersion?: string
    tosVersion?: string
}

export interface ConsentChangesResponse {
    privacyChanged: boolean
    tosChanged: boolean
    privacyDiff?: string
    tosDiff?: string
    privacyHtml?: string
    tosHtml?: string
    currentPrivacyVersion: string
    currentTosVersion: string
    currentConsentVersion: string
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

// -- Event Fields --

export interface EventField {
    id: number
    eventId: number
    name?: string
    value?: string
    position: number
}

export interface SetEventFieldsRequest {
    fields: { name: string; value?: string }[]
}

// -- Forms --

export const FormStatus = {
    DRAFT: 'DRAFT',
    OPEN: 'OPEN',
    CLOSED: 'CLOSED',
} as const

export type FormStatusName = (typeof FormStatus)[keyof typeof FormStatus]

export const QuestionTypes = {
    CHOICE: 'CHOICE',
    TEXT: 'TEXT',
    RATING: 'RATING',
    DATE: 'DATE',
    RANKING: 'RANKING',
    LIKERT: 'LIKERT',
} as const

export type QuestionType = (typeof QuestionTypes)[keyof typeof QuestionTypes]
export type MultiLimitType = 'NONE' | 'EQUAL_TO' | 'AT_MOST' | 'AT_LEAST'
export type RatingIcon = 'STAR' | 'NUMBER' | 'HEART' | 'THUMB_UP'

export interface Form {
    id: number
    stationId: number
    title: string
    description: string
    status: FormStatusName
    shuffleQuestions: boolean
    allowEdit: boolean
    startAt?: string | null
    endAt?: string | null
    closedAt?: string | null
    createdBy: number
    createdAt: string
    updatedAt: string
}

export interface FormListEntry {
    id: number
    stationId: number
    title: string
    description: string
    status: string
    startAt?: string | null
    endAt?: string | null
    responseCount: number
    hasResponded: boolean
}

export interface FormQuestion {
    id: number
    formId: number
    position: number
    questionType: QuestionType
    title: string
    description: string
    required: boolean
    shuffle: boolean
    config: string
}

export interface FormResponse {
    id: number
    formId: number
    memberId: number
    submittedBy: number
    submittedByName?: string | null
    submittedAt: string
    updatedAt: string
}

export interface FormAnswer {
    id: number
    responseId: number
    questionId: number
    value: string
}

export interface FormRequest {
    title: string
    description?: string
    shuffleQuestions?: boolean
    allowEdit?: boolean
    startAt?: string | null
    endAt?: string | null
}

export interface FormQuestionRequest {
    questionType: string
    title: string
    description?: string
    required?: boolean
    shuffle?: boolean
    config?: string
}

export interface FormRestrictions {
    roleIds: number[]
    groupIds: number[]
    tagIds: number[]
}

export interface FormSubmitRequest {
    answers: Record<number, string>
}

export interface FormResponseDetail {
    response: FormResponse | null
    answers: FormAnswer[]
}

export interface FormAnalytics {
    formId: number
    totalResponses: number
    questions: FormQuestionAnalytics[]
}

export interface FormQuestionAnalytics {
    questionId: number
    questionType: string
    title: string
    config: string
    values: string[]
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
    profileComplete?: boolean
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

// -- Registration Status --

export const RegistrationStatus = {
    PENDING: 'PENDING',
    ACCEPTED: 'ACCEPTED',
    DENIED: 'DENIED',
    DECLINED: 'DECLINED',
} as const

export type RegistrationStatusName = (typeof RegistrationStatus)[keyof typeof RegistrationStatus]

// -- Event Type --

export const EventTypes = {
    ONE_TIME: 'ONE_TIME',
    RECURRING: 'RECURRING',
    MONTHLY_FIRST: 'MONTHLY_FIRST',
    QUARTERLY: 'QUARTERLY',
    YEARLY: 'YEARLY',
} as const

export type EventTypeName = (typeof EventTypes)[keyof typeof EventTypes]

export function isRecurringEvent(eventType?: string): boolean {
    return eventType != null && eventType !== EventTypes.ONE_TIME
}

export function needsDayOfWeek(eventType?: string): boolean {
    return eventType === EventTypes.RECURRING || eventType === EventTypes.MONTHLY_FIRST || eventType === EventTypes.QUARTERLY
}

// -- Profile Field Type --

export const FieldTypes = {
    TEXT: 'text',
    NUMBER: 'number',
    DATE: 'date',
    BOOLEAN: 'boolean',
    ENUM: 'enum',
    AGE: 'age',
} as const

export type FieldTypeName = (typeof FieldTypes)[keyof typeof FieldTypes]

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

export const InventoryTypes = {
    INTERNAL: 'INTERNAL',
    EXTERNAL: 'EXTERNAL',
    MIXED: 'MIXED',
} as const

export type InventoryTypeName = (typeof InventoryTypes)[keyof typeof InventoryTypes]

export const ItemSource = {
    INTERNAL: 'INTERNAL',
    EXTERNAL: 'EXTERNAL',
} as const

export type ItemSourceName = (typeof ItemSource)[keyof typeof ItemSource]

export interface Inventory {
    id: number
    stationId: number
    name?: string
    inventoryType?: InventoryTypeName
    hasSizes: boolean
}

export interface InventoryRequest {
    name?: string
    inventoryType?: InventoryTypeName
    hasSizes: boolean
}

export interface InventoryDetail {
    id: number
    stationId: number
    name?: string
    inventoryType?: InventoryTypeName
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
    itemSource?: string | null
}

export interface ItemRequest {
    internalId?: string
    name?: string
    sizeId?: number
    metadata?: string
    itemSource?: string
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
    keepOnArchive?: boolean
}

export interface ProfileFieldRequest {
    name?: string
    fieldType?: string
    config?: string
    position: number
    scope?: string
    keepOnArchive?: boolean
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
    memberName?: string | null
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

export interface NotificationToggle {
    app: boolean
    email: boolean
}

export interface UserSettings {
    emailEnabled: boolean
    notifications: Record<string, NotificationToggle>
    mailConfigured: boolean
    mailProviderName: string
    mailProviderUrl: string
}

export interface UserSettingsRequest {
    emailEnabled: boolean
    notifications: Record<string, NotificationToggle>
}

// -- Equipment Exchange --

export const ExchangeStatus = {
    ANNOUNCED: 'ANNOUNCED',
    RECEIVED: 'RECEIVED',
    SHIPPED: 'SHIPPED',
    ARRIVED: 'ARRIVED',
    EXCHANGED: 'EXCHANGED',
} as const

export type ExchangeStatusName = (typeof ExchangeStatus)[keyof typeof ExchangeStatus]

export interface ExchangeRequestEntry {
    id: number
    memberId: number
    memberName: string
    itemId?: number | null
    inventoryId: number
    inventoryName: string
    oldSizeId?: number | null
    oldSizeLabel?: string | null
    newSizeId?: number | null
    newSizeLabel?: string | null
    inventoryType: string
    status: ExchangeStatusName
    reason: string
    createdAt: string
    updatedAt: string
    createdByName?: string | null
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
    memberId?: number | null
    itemId?: number | null
    inventoryId: number
    oldSizeId?: number | null
    newSizeId?: number | null
    reason: string
}

export interface UpdateStatusRequest {
    status: string
    note?: string
    exchangedItemId?: number | null
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

export type NotificationType = 'NEW_NEWS' | 'NEWS_COMMENT' | 'EVENT_REGISTRATION_STATUS' | 'EXCHANGE_STATUS_CHANGE'
    | 'EXCHANGE_NEW_REQUEST' | 'NEW_EVENT' | 'MEMBER_ADDED_TO_GROUP' | 'PROFILE_FIELD_CHANGED'
    | 'PROCUREMENT_REQUESTED' | 'PROCUREMENT_FULFILLED' | 'LOST_AND_FOUND_NEW' | 'LOST_AND_FOUND_CLAIMED'

export interface NotificationLink {
    route: string
    routeParams?: Record<string, string | number>
}

export interface NotificationEntry {
    id: number
    type: NotificationType
    localeKey: string
    params: Record<string, string>
    link?: NotificationLink | null
    createdAt: string
    acknowledgedAt?: string | null
}

// -- Lost and Found --

export interface LostAndFoundItem {
    id: number
    stationId: number
    description?: string
    foundAt?: string
    hasImage: boolean
    claimedBy?: number | null
    claimedByName?: string | null
    claimedAt?: string | null
    createdBy: number
    createdAt: string
}

export interface CreateLostAndFoundRequest {
    description?: string
    foundAt?: string
}

export interface ClaimLostAndFoundRequest {
    memberId?: number | null
}
