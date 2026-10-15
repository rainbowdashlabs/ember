/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {type Board, type EnrichedBookmark, type SidebarCounts, StationModule, StationPermission} from '@/api/generated/schema'
import type {useSession} from '@/composables/useSession'
import type {NavEntryDef, NavPageDef} from '@/data/sidebar/navTypes'

/** One step of the station's first setup, as its sidebar entry shows it. */
export interface SetupNavStep {
    key: string
    to: string
    label: string
    complete: boolean
    applicable: boolean
}

/** What the station sidebar's conditions read: the reader's session, the counts and the runtime lists. */
export interface StationNavContext {
    session: ReturnType<typeof useSession>
    counts: SidebarCounts
    boards: readonly Pick<Board, 'id' | 'name' | 'shortKey'>[]
    boardBookmarks: readonly Pick<EnrichedBookmark, 'id' | 'partnerStationUid' | 'remoteBoardShortKey' | 'remoteBoardName'>[]
    setupSteps: readonly SetupNavStep[]
}

type Entry = NavEntryDef<StationNavContext>
type Page = NavPageDef<StationNavContext>
type Condition = (context: StationNavContext) => boolean

function can(permission: StationPermission): Condition {
    return ({session}) => session.hasPermission(permission)
}

function canAny(...permissions: StationPermission[]): Condition {
    return ({session}) => permissions.some(permission => session.hasPermission(permission))
}

function runs(module: StationModule): Condition {
    return ({session}) => session.isModuleEnabled(module)
}

function both(first: Condition, second: Condition): Condition {
    return context => first(context) && second(context)
}

/**
 * The media library is shared by everything that authors content, so any one of the three content
 * permissions opens it. Members without one still reach it through an editor, where they see what
 * they uploaded themselves.
 */
const canBrowseMedia = canAny(StationPermission.PAGE_EDIT, StationPermission.NEWS_EDIT, StationPermission.KNOWLEDGE_EDIT)

/**
 * Whether tests belong in the quiz section for this reader: the station runs them, and the reader
 * may write a test sheet or sit one. Somebody who may do neither sees the section as a station that
 * runs no tests shows it.
 */
const takesProtocols: Condition = context => runs(StationModule.TEST_PROTOCOL)(context)
    && (can(StationPermission.PROTOCOL_CREATE)(context) || context.session.canTestProtocol())

const runsQuiz = runs(StationModule.QUIZ)

/**
 * Named after the halves it still carries: a station that runs its tests but no quiz should not read
 * "Quiz" in the sidebar.
 */
function quizLabel(context: StationNavContext): string {
    if (runsQuiz(context) && takesProtocols(context)) return 'sidebar.quizAndProtocols'
    if (takesProtocols(context)) return 'sidebar.protocolsGroup'
    return 'sidebar.quiz'
}

/** With only one of quiz and tests there is nothing to separate, and a subgroup would be a level for its own sake. */
const quizFlat: Condition = context => !(runsQuiz(context) && takesProtocols(context))

function boardPages({boards, boardBookmarks}: StationNavContext): Page[] {
    const own = boards.map((board): Page => ({
        kind: 'page',
        key: `board-${board.id}`,
        label: 'sidebar.boards',
        text: board.name,
        icon: 'table-columns',
        to: `/station/boards/${board.shortKey}`,
    }))
    const bookmarked = boardBookmarks.map((bookmark): Page => ({
        kind: 'page',
        key: `fed-board-${bookmark.id}`,
        label: 'sidebar.boards',
        text: bookmark.remoteBoardName,
        icon: 'table-columns',
        trailingIcon: 'arrow-right-arrow-left',
        to: `/station/federation/boards/${bookmark.partnerStationUid}/${bookmark.remoteBoardShortKey}`,
    }))
    return [...own, ...bookmarked]
}

function setupPages({setupSteps}: StationNavContext): Page[] {
    return setupSteps.map((step): Page => ({
        kind: 'page',
        key: step.key,
        label: step.label,
        icon: step.complete ? 'circle-check' : 'circle',
        to: step.to,
        dimmed: () => !step.applicable,
    }))
}

const QUIZ_PAGES: Page[] = [
    {kind: 'page', key: 'quiz-catalogs', label: 'sidebar.quizCatalogs', icon: 'book', to: '/station/quiz/catalogs', visible: can(StationPermission.TEST_CATALOG_VIEW)},
    {kind: 'page', key: 'quiz-tests', label: 'sidebar.quizTests', icon: 'file-lines', to: '/station/quiz/tests'},
    {kind: 'page', key: 'quiz-training', label: 'sidebar.quizTraining', icon: 'brain', to: '/station/quiz/training', onboarding: 'nav.quiz.training'},
]

const PROTOCOL_PAGES: Page[] = [
    {kind: 'page', key: 'protocol-list', label: 'sidebar.protocols', icon: 'clipboard-list', to: '/station/protocols', visible: can(StationPermission.PROTOCOL_CREATE)},
    {kind: 'page', key: 'protocol-run-list', label: 'sidebar.protocolRuns', icon: 'clipboard-check', to: '/station/protocols/runs', visible: ({session}) => session.canTestProtocol()},
]

/** The sidebar of a station that has moved to another installation: only what is left to do here. */
export const MOVED_STATION_NAV: Entry[] = [
    {kind: 'page', key: 'station-moved', label: 'sidebar.stationMoved', icon: 'map-location-dot', to: '/station/moved'},
    {kind: 'page', key: 'station-moved-delete', label: 'pages.station-moved.tabDelete', icon: 'trash', to: '/station/moved/delete', visible: can(StationPermission.STATION_ADMINISTRATOR)},
]

/** The station sidebar, in order. */
export const STATION_NAV: Entry[] = [
    {
        kind: 'group', key: 'setup', label: 'setup.headerTitle', icon: 'rocket', prefix: ['/station/setup'],
        visible: context => can(StationPermission.STATION_ADMINISTRATOR)(context) && context.session.sessionInfo.value?.setupCompletedAt == null,
        children: [
            {kind: 'page', key: 'station-setup', label: 'sidebar.overview', icon: 'list-check', to: '/station/setup'},
            setupPages,
        ],
    },
    {
        kind: 'group', key: 'dashboard', label: 'sidebar.dashboard', icon: 'gauge',
        badge: ({counts}) => counts.notifications,
        children: [
            {kind: 'page', key: 'dashboard-overview', label: 'sidebar.home', icon: 'house', to: '/station/dashboard/overview', badge: ({counts}) => counts.notifications},
            {kind: 'page', key: 'dashboard-statistics', label: 'sidebar.statistics', icon: 'chart-line', to: '/station/dashboard/statistics', visible: can(StationPermission.STATION_STATISTICS)},
        ],
    },
    {kind: 'page', key: 'station-requirements', label: 'sidebar.requirements', icon: 'clipboard-check', to: '/station/requirements', badge: ({counts}) => counts.requirements, visible: ({counts}) => counts.requirements > 0},
    {kind: 'page', key: 'news-list', label: 'sidebar.news', icon: 'newspaper', to: '/station/news', visible: runs(StationModule.NEWS)},
    {
        kind: 'group', key: 'profile-section', label: 'sidebar.profile', icon: 'user', onboarding: 'nav.profile',
        children: [
            {kind: 'page', key: 'profile', label: 'sidebar.myProfile', icon: 'user', to: '/station/profile', onboarding: 'nav.profile'},
            {kind: 'page', key: 'profile-absences', label: 'sidebar.absences', icon: 'calendar-days', to: '/station/profile/absences', onboarding: 'nav.profile.absences'},
            {kind: 'page', key: 'profile-managed', label: 'sidebar.managedProfiles', icon: 'users', to: '/station/profile/managed', onboarding: 'nav.profile.managed', visible: ({session}) => session.isGuardian()},
            {kind: 'page', key: 'profile-notifications', label: 'sidebar.notifications', icon: 'bell', to: '/station/profile/settings/notifications', onboarding: 'nav.profile.notifications'},
        ],
    },
    {
        kind: 'group', key: 'members', label: 'sidebar.members', icon: 'users', prefix: ['/station/members'], onboarding: 'nav.members',
        visible: ({session}) => session.hasAnyMemberPermission() || session.hasAnyWaitlistPermission(),
        badge: ({counts}) => counts.pendingChanges + counts.waitingListEntries,
        children: [
            {kind: 'page', key: 'members-list', label: 'sidebar.memberList', icon: 'users', to: '/station/members/list', visible: can(StationPermission.MEMBER_READ)},
            {kind: 'page', key: 'members-create', label: 'sidebar.create', icon: 'user-plus', to: '/station/members/create', onboarding: 'nav.members.create', visible: can(StationPermission.MEMBER_EDIT)},
            {kind: 'page', key: 'members-groups', label: 'sidebar.groups', icon: 'layer-group', to: '/station/members/groups', onboarding: 'nav.members.groups', visible: can(StationPermission.MEMBER_MANAGE_GROUP)},
            {kind: 'page', key: 'members-tags', label: 'sidebar.tags', icon: 'hashtag', to: '/station/members/tags', visible: can(StationPermission.MEMBER_MANAGE_TAGS)},
            {kind: 'page', key: 'members-changes', label: 'sidebar.changes', icon: 'bell', to: '/station/members/changes', badge: ({counts}) => counts.pendingChanges, visible: can(StationPermission.MEMBER_CHANGES)},
            {kind: 'page', key: 'members-former', label: 'sidebar.formerMembers', icon: 'user-slash', to: '/station/members/former', visible: can(StationPermission.MEMBER_EDIT)},
            {
                kind: 'page', key: 'waiting-lists', label: 'sidebar.waitingLists', icon: 'clipboard-list', to: '/station/members/waiting-lists',
                badge: ({counts}) => counts.waitingListEntries,
                visible: context => runs(StationModule.WAITING_LIST)(context) && context.session.hasAnyWaitlistPermission(),
            },
            {
                kind: 'group', key: 'members-settings', label: 'sidebar.settings', icon: 'gears',
                children: [
                    {kind: 'page', key: 'members-type-permissions', label: 'sidebar.typePermissions', icon: 'shield', to: '/station/members/type-permissions', onboarding: 'nav.members.type-permissions', visible: ({session}) => session.canManageMembers()},
                    {kind: 'page', key: 'station-members-config', label: 'sidebar.membersConfig', icon: 'users-gear', to: '/station/members/config', visible: can(StationPermission.MEMBER_FIELDS)},
                ],
            },
        ],
    },
    {
        kind: 'group', key: 'documents', label: 'sidebar.documents', icon: 'file', visible: runs(StationModule.DOCUMENTS),
        children: [
            {kind: 'page', key: 'documents-own', label: 'sidebar.ownDocuments', icon: 'file', to: '/station/documents'},
            {kind: 'page', key: 'documents-self-service', label: 'sidebar.documentSelfService', icon: 'file-circle-plus', to: '/station/documents/self-service'},
            {kind: 'page', key: 'documents-store', label: 'sidebar.documentStore', icon: 'box-archive', to: '/station/documents/store', visible: can(StationPermission.DOCUMENT_READ)},
            {kind: 'page', key: 'documents-templates', label: 'sidebar.documentTemplates', icon: 'file-signature', to: '/station/documents/templates', visible: can(StationPermission.DOCUMENT_TEMPLATE_EDIT)},
            {kind: 'page', key: 'documents-generated', label: 'sidebar.generatedDocuments', icon: 'clock-rotate-left', to: '/station/documents/generated', visible: can(StationPermission.DOCUMENT_READ_MEMBER)},
            {kind: 'page', key: 'documents-fonts', label: 'sidebar.documentFonts', icon: 'font', to: '/station/documents/fonts', visible: can(StationPermission.DOCUMENT_TEMPLATE_EDIT)},
        ],
    },
    {
        kind: 'group', key: 'inventory', label: 'sidebar.inventory', icon: 'boxes-stacked', prefix: ['/station/inventory'],
        visible: runs(StationModule.INVENTORY),
        badge: ({counts}) => counts.openMovements + counts.lendingRequests,
        children: [
            {kind: 'page', key: 'inventory-overview', label: 'sidebar.inventoryOverview', icon: 'boxes-stacked', to: '/station/inventory', visible: can(StationPermission.INVENTORY_READ)},
            {kind: 'page', key: 'inventory-my', label: 'sidebar.myInventory', icon: 'boxes-stacked', to: '/station/inventory/my', visible: ({counts}) => counts.myInventoryCount > 0},
            {kind: 'page', key: 'inventory-members', label: 'sidebar.inventoryMembers', icon: 'users', to: '/station/inventory/members', visible: can(StationPermission.INVENTORY_READ)},
            {kind: 'page', key: 'inventory-assign', label: 'sidebar.inventoryAssign', icon: 'user-plus', to: '/station/inventory/assign', visible: can(StationPermission.INVENTORY_ASSIGN)},
            {kind: 'page', key: 'inventory-storage', label: 'sidebar.inventoryStorage', icon: 'warehouse', to: '/station/inventory/storage', visible: can(StationPermission.INVENTORY_READ)},
            {kind: 'page', key: 'inventory-movements', label: 'sidebar.inventoryMovements', icon: 'rotate', to: '/station/inventory/movements', badge: ({counts}) => counts.openMovements},
            {kind: 'page', key: 'inventory-procurement', label: 'sidebar.inventoryProcurement', icon: 'folder-plus', to: '/station/inventory/procurement', visible: can(StationPermission.INVENTORY_PROCUREMENT)},
            {
                kind: 'group', key: 'inventory-checks', label: 'sidebar.inventoryCheck', icon: 'clipboard-check', prefix: ['/station/inventory/checks'], visible: can(StationPermission.INVENTORY_CHECK),
                children: [
                    {kind: 'page', key: 'inventory-check-member-overview', label: 'sidebar.inventoryCheckMember', icon: 'user-check', to: '/station/inventory/checks/member'},
                    {kind: 'page', key: 'inventory-check-container-overview', label: 'sidebar.inventoryCheckContainer', icon: 'box-open', to: '/station/inventory/checks/container'},
                ],
            },
            {
                kind: 'group', key: 'inventory-settings', label: 'sidebar.inventorySettings', icon: 'gears',
                children: [
                    {kind: 'page', key: 'inventory-manage', label: 'sidebar.inventoryManage', icon: 'box-open', to: '/station/inventory/manage', visible: can(StationPermission.INVENTORY_CREATE)},
                    {kind: 'page', key: 'inventory-flows', label: 'sidebar.inventoryFlows', icon: 'diagram-project', to: '/station/inventory/flows', visible: can(StationPermission.INVENTORY_MANAGER)},
                    {kind: 'page', key: 'inventory-requirements', label: 'sidebar.inventoryRequirements', icon: 'clipboard-list', to: '/station/inventory/requirements', visible: can(StationPermission.INVENTORY_READ)},
                ],
            },
            {
                kind: 'group', key: 'inventory-lending-group', label: 'sidebar.inventoryLendingGroup', icon: 'handshake', badge: ({counts}) => counts.lendingRequests,
                children: [
                    {kind: 'page', key: 'inventory-borrowed', label: 'sidebar.inventoryBorrowed', icon: 'hand-holding', to: '/station/inventory/borrowed', visible: can(StationPermission.INVENTORY_READ)},
                    {
                        kind: 'page', key: 'inventory-lending', label: 'sidebar.inventoryLending', icon: 'handshake', to: '/station/inventory/lending',
                        badge: ({counts}) => counts.lendingRequests,
                        visible: canAny(StationPermission.INVENTORY_LENDING_REQUEST, StationPermission.INVENTORY_LENDING_MANAGER),
                    },
                    {kind: 'page', key: 'inventory-lending-shares', label: 'sidebar.inventoryLendingShares', icon: 'share-nodes', to: '/station/inventory/lending/shares', paletteOnly: true, visible: can(StationPermission.INVENTORY_LENDING_MANAGER)},
                ],
            },
        ],
    },
    {
        kind: 'group', key: 'attendance', label: 'sidebar.attendance', icon: 'clipboard-user', prefix: ['/station/attendance'],
        visible: context => context.session.hasAnyAttendancePermission() && runs(StationModule.ATTENDANCE)(context),
        children: [
            {kind: 'page', key: 'attendance-new', label: 'sidebar.newAttendance', icon: 'clipboard-user', to: '/station/attendance/new', visible: can(StationPermission.ATTENDANCE_EDIT)},
            {kind: 'page', key: 'attendance-past', label: 'sidebar.pastAttendance', icon: 'clock-rotate-left', to: '/station/attendance/past', visible: can(StationPermission.ATTENDANCE_READ)},
            {kind: 'page', key: 'attendance-report', label: 'sidebar.attendanceReport', icon: 'chart-line', to: '/station/attendance/report', visible: ({session}) => session.canExportAttendance()},
            {kind: 'page', key: 'station-attendance-config', label: 'sidebar.attendanceConfig', icon: 'gear', to: '/station/attendance/config', visible: can(StationPermission.ATTENDANCE_CONFIGURE)},
        ],
    },
    {
        kind: 'group', key: 'events-section', label: 'sidebar.events', icon: 'calendar-days', onboarding: 'nav.events.upcoming',
        visible: runs(StationModule.EVENTS),
        badge: ({counts}) => counts.pendingRegistrations + counts.openEvents,
        children: [
            {kind: 'page', key: 'events-upcoming', label: 'sidebar.nextEvents', icon: 'calendar-days', to: '/station/events/upcoming', onboarding: 'nav.events.upcoming', badge: ({counts}) => counts.openEvents},
            {kind: 'page', key: 'events-registrations', label: 'sidebar.pendingRegistrations', icon: 'clipboard-list', to: '/station/events/registrations', badge: ({counts}) => counts.pendingRegistrations, visible: can(StationPermission.EVENT_REGISTRATION)},
            {kind: 'page', key: 'events', label: 'sidebar.manageEvents', icon: 'gears', to: '/station/events', onboarding: 'nav.events', visible: can(StationPermission.EVENT_EDIT)},
            {kind: 'page', key: 'event-categories', label: 'sidebar.eventCategories', icon: 'folder-plus', to: '/station/events/categories', visible: can(StationPermission.EVENT_MANAGE_CATEGORY)},
            {kind: 'page', key: 'event-templates', label: 'sidebar.eventTemplates', icon: 'clipboard-list', to: '/station/events/templates', visible: can(StationPermission.EVENT_MANAGE_TEMPLATE)},
        ],
    },
    {
        kind: 'group', key: 'forms', label: 'sidebar.forms', icon: 'square-poll-vertical', visible: runs(StationModule.FORMS),
        children: [
            {kind: 'page', key: 'forms-list', label: 'sidebar.formsList', icon: 'square-poll-vertical', to: '/station/forms'},
            {kind: 'page', key: 'forms-create', label: 'sidebar.formsCreate', icon: 'plus', to: '/station/forms/create', visible: can(StationPermission.POLL_CREATE)},
        ],
    },
    {kind: 'page', key: 'lost-and-found', label: 'sidebar.lostAndFound', icon: 'box-open', to: '/station/lost-and-found', badge: ({counts}) => counts.lostAndFoundPending, visible: runs(StationModule.LOST_AND_FOUND)},
    {
        kind: 'group', key: 'quiz-protocols', label: quizLabel, icon: 'graduation-cap', prefix: ['/station/quiz', '/station/protocols'], onboarding: 'nav.quiz',
        children: [
            {kind: 'group', key: 'quiz', label: 'sidebar.quiz', icon: 'graduation-cap', prefix: ['/station/quiz'], visible: runsQuiz, inline: quizFlat, children: QUIZ_PAGES},
            {kind: 'group', key: 'protocols', label: 'sidebar.protocolsGroup', icon: 'clipboard-list', prefix: ['/station/protocols'], visible: takesProtocols, inline: quizFlat, children: PROTOCOL_PAGES},
        ],
    },
    {
        kind: 'group', key: 'boards', label: 'sidebar.boards', icon: 'table-columns',
        visible: both(runs(StationModule.BOARDS), can(StationPermission.BOARD_USE)),
        children: [
            {kind: 'page', key: 'board-list', label: 'sidebar.boardList', icon: 'table-columns', to: '/station/boards'},
            boardPages,
            {kind: 'page', key: 'federated-boards', label: 'boards.federatedBoards', icon: 'globe', to: '/station/federation/boards'},
            {kind: 'page', key: 'board-manage', label: 'sidebar.boardManage', icon: 'gears', to: '/station/boards/manage', visible: can(StationPermission.BOARD_EDIT)},
        ],
    },
    {
        kind: 'group', key: 'procedures', label: 'sidebar.procedures', icon: 'list-check',
        badge: ({counts}) => counts.procedureCount,
        visible: context => runs(StationModule.PROCEDURES)(context) && (can(StationPermission.PROCEDURE_READ)(context) || context.counts.procedureCount > 0),
        children: [
            {kind: 'page', key: 'procedure-list', label: 'sidebar.procedureList', icon: 'list-check', to: '/station/procedures', badge: ({counts}) => counts.procedureCount},
            {kind: 'page', key: 'procedure-template-list', label: 'sidebar.procedureTemplates', icon: 'clipboard-list', to: '/station/procedures/templates', visible: can(StationPermission.PROCEDURE_MANAGER)},
        ],
    },
    {kind: 'page', key: 'checklist-list', label: 'sidebar.checklists', icon: 'list-check', to: '/station/checklist', visible: can(StationPermission.CHECKLIST_READ)},
    {kind: 'page', key: 'kb-browse', label: 'sidebar.knowledgeBase', icon: 'book-open', to: '/station/knowledge', onboarding: 'nav.knowledge', visible: runs(StationModule.KNOWLEDGE_BASE)},
    {kind: 'page', key: 'station-media', label: 'sidebar.media', icon: 'folder-open', to: '/station/media', visible: canBrowseMedia},
    {
        kind: 'group', key: 'pages', label: 'sidebar.pages', icon: 'file-lines', visible: can(StationPermission.PAGE_EDIT),
        children: [
            {kind: 'page', key: 'pages-list', label: 'sidebar.pageList', icon: 'file-lines', to: '/station/pages'},
            {kind: 'page', key: 'pages-forms', label: 'sidebar.pagesForms', icon: 'clipboard-list', to: '/station/pages/forms'},
            {kind: 'page', key: 'pages-polls', label: 'sidebar.pagesPolls', icon: 'square-poll-vertical', to: '/station/pages/polls'},
        ],
    },
    {
        kind: 'group', key: 'manage', label: 'sidebar.manage', icon: 'gears', prefix: ['/station/manage'], onboarding: 'nav.manage',
        visible: ({session}) => session.isManager(),
        children: [
            {kind: 'page', key: 'station-manage', label: 'sidebar.general', icon: 'sliders', to: '/station/manage', onboarding: 'nav.manage', visible: can(StationPermission.STATION_GENERAL)},
            {kind: 'page', key: 'station-theme', label: 'sidebar.stationTheme', icon: 'palette', to: '/station/manage/theme', visible: can(StationPermission.STATION_LOOK_AND_FEEL)},
            {kind: 'page', key: 'station-mailing', label: 'sidebar.stationMailing', icon: 'envelope', to: '/station/manage/mailing', onboarding: 'nav.manage.mailing', visible: can(StationPermission.STATION_MAIL)},
            {kind: 'page', key: 'station-mail-import', label: 'sidebar.stationMailImport', icon: 'envelope-open-text', to: '/station/manage/mail-import', visible: both(can(StationPermission.STATION_MAIL), runs(StationModule.DOCUMENTS))},
            {kind: 'page', key: 'station-modules', label: 'sidebar.stationModules', icon: 'puzzle-piece', to: '/station/manage/modules', visible: can(StationPermission.STATION_MODULES)},
            {kind: 'page', key: 'station-manage-cluster', label: 'sidebar.stationCluster', icon: 'sitemap', to: '/station/manage/cluster', visible: can(StationPermission.STATION_GENERAL)},
            {kind: 'page', key: 'station-import', label: 'sidebar.stationImport', icon: 'file-import', to: '/station/manage/import', visible: can(StationPermission.STATION_IMPORT_EXPORT)},
            {kind: 'page', key: 'station-storage-backend', label: 'sidebar.storageBackend', icon: 'hard-drive', to: '/station/manage/storage/backend', paletteOnly: true, visible: can(StationPermission.STATION_ADMINISTRATOR)},
            {kind: 'page', key: 'station-security', label: 'sidebar.stationSecurity', icon: 'shield', to: '/station/manage/security', visible: can(StationPermission.STATION_ADMINISTRATOR)},
        ],
    },
    {
        kind: 'group', key: 'station-monitoring', label: 'sidebar.monitoring', icon: 'chart-line', visible: can(StationPermission.STATION_ADMINISTRATOR),
        children: [
            {kind: 'page', key: 'station-traffic', label: 'sidebar.stationTraffic', icon: 'tower-broadcast', to: '/station/monitoring/traffic'},
            {kind: 'page', key: 'station-insights', label: 'sidebar.stationInsights', icon: 'chart-pie', to: '/station/monitoring/insights'},
            {kind: 'page', key: 'station-feeds', label: 'sidebar.stationFeeds', icon: 'rss', to: '/station/monitoring/feeds'},
            {kind: 'page', key: 'station-storage', label: 'sidebar.storage', icon: 'hard-drive', to: '/station/monitoring/storage'},
        ],
    },
    {
        kind: 'group', key: 'federation', label: 'sidebar.federation', icon: 'arrow-right-arrow-left',
        visible: ({session}) => session.canManageFederation(),
        badge: ({counts}) => counts.federationRequests,
        children: [
            {kind: 'page', key: 'station-federation', label: 'sidebar.federationPartners', icon: 'handshake', to: '/station/federate'},
            {kind: 'page', key: 'station-federation-settings', label: 'sidebar.federationSettings', icon: 'gear', to: '/station/federate/settings', onboarding: 'nav.federation', badge: ({counts}) => counts.federationRequests},
            {kind: 'page', key: 'station-discovery', label: 'sidebar.discovery', icon: 'compass', to: '/station/federate/discovery'},
        ],
    },
]
