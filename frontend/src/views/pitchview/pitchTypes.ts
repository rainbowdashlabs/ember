/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {AttendanceEntry} from '@/api/attendance'
import type {InventoryItem, InventorySize} from '@/api/inventory'
import type {InventoryContainer, InventoryContainerKind} from '@/api/inventoryContainers'
import type {CheckResult, RequiredInventoryItem} from '@/api/inventoryCheck'
import type {CheckEntry} from '@/composables/useMemberCheck'
import type {EvaluationResponse, TestProtocolItem, TestProtocolSection} from '@/api/protocol'
import type {Form, FormQuestion, FormQuestionAnalytics} from '@/api/forms'
import type {PageRow, StationPage} from '@/api/pageManage'
import type {UserSettings} from '@/api/userSettings'
import type {ActiveSession} from '@/api/session'
import type {
    WaitingList, WaitingListEntryWithScore, WaitingListField, WaitingListWithCount,
} from '@/api/waitingList'
import type {ProcedureItem, ProcedureTemplateItem} from '@/api/procedures'
import type {QuizCatalog} from '@/api/quiz'
import type {KbItem} from '@/views/stationview/knowledge/knowledgebaseview/useKbItems'
import type {BoardLabel, BoardLane, BoardTicket} from '@/api/boards'
import type {Comment} from '@/api/comments'
import type {MemberCompletion} from '@/api/stationMembers'
import type {MemberGroup, MemberIdentity, StationMember, UserTag} from '@/api/types'
import type {PartnerResponse} from '@/api/federation'

/** The accent a slide is drawn in. Maps onto the theme colours, not onto fixed hex values. */
export type Accent = 'primary' | 'secondary' | 'success' | 'info' | 'error'

export interface PitchCard {
    /** A FontAwesome solid icon name, as the application uses them. */
    icon?: string
    /** Shown instead of an icon where a card is numbered. */
    label?: string
    title: string
    body: string
    accent?: Accent
}

export interface PitchCell {
    text: string
    /** Drawn as a badge of the application's badge family. */
    badge?: Accent
    muted?: boolean
    /** The primary-coloured name column the application uses for the leading cell. */
    strong?: boolean
    /** Preceded by the member avatar, as the member lists show names. */
    avatar?: boolean
    /** A second, smaller badge behind the text - the way the lists flag an incomplete profile. */
    note?: {text: string; tone: Accent}
}

export interface PitchTable {
    columns: string[]
    rows: PitchCell[][]
    /** Adds the leading column of row actions the management tables carry. */
    actions?: boolean
}

/**
 * An entry as the event, news and knowledge lists draw them: the name as a primary link, muted
 * meta beside it, the overview fields underneath and the summary badges on the right.
 */
export interface PitchListRow {
    /** The monospaced chip before the name, as a board carries its key. */
    prefix?: string
    name: string
    /** Shown as the lock the restricted entries carry. */
    locked?: boolean
    /** The name stays plain instead of reading as a link, as the run and sheet lists draw it. */
    plain?: boolean
    /** The partner badge a shared entry carries beside its name. */
    station?: string
    meta?: string[]
    /** The muted second line the sheet and run lists put under the name. */
    description?: string
    fields?: {label: string; value: string}[]
    badges?: {text: string; tone: Accent}[]
    /** A monospaced figure before the badges, as the score of a graded sheet. */
    score?: string
    /** A muted note at the end of the row, as the pass mark a sheet carries. */
    trailing?: string
    /** The bookmark star a federated board carries, lit when it is set. */
    favourite?: boolean
    /** The compact button at the end of the row. */
    action?: string
    /** The icon of that button; the sheet lists use the clipboard it defaults to. */
    actionIcon?: string
    /** Drawn on the accented ground the today's-events cards carry. */
    highlight?: boolean
}

/**
 * What the application's own board needs to draw itself: the lanes, the cards, and the labels
 * per card. The types are the ones the board really speaks, so a wrong preview fails the build.
 */
export interface PitchBoard {
    shortKey: string
    lanes: BoardLane[]
    tickets: BoardTicket[]
    labels?: Record<number, BoardLabel[]>
    members?: MemberCompletion[]
    archivedCount?: number
}

/** What the application's own container tree needs to draw the storage. */
export interface PitchStorage {
    roots: InventoryContainer[]
    childrenByParent: Map<number, InventoryContainer[]>
    kindById: Map<number, InventoryContainerKind>
}

/** What the application's own statistics panel shows above an inventory. */
export interface PitchStats {
    totalCount: number
    freeCount: number
    assignedCount: number
    lostCount: number
    lentOutCount: number
    hasSizes: boolean
    sizeStats: {size: InventorySize | null; total: number; assigned: number; free: number; lost: number}[]
}

/** What the application's own check panel needs: the entry it stands on, and how far it has come. */
export interface PitchCheckMode {
    entry: AttendanceEntry | null
    index: number
    total: number
    name: string
    identity: MemberIdentity | null
}

/** What the application's own check section needs: the requirement, the items, the marks. */
export interface PitchInventoryCheck {
    req: RequiredInventoryItem
    assignedItems: InventoryItem[]
    availableItems: InventoryItem[]
    emptySlotCount: number
    itemResults: Map<number, CheckResult>
    itemNotes: Map<number, string>
    procurementCreated: Set<number>
    slotsNotInPossession: Set<string>
    slotSelections: Map<string, string>
    sizeLabel: (req: RequiredInventoryItem, sizeId?: number | null) => string
    itemLabel: (item: InventoryItem, req: RequiredInventoryItem) => string
}

/** What the application's own quick check needs to walk through the open items. */
export interface PitchRapidCheck {
    uncheckedEntries: CheckEntry[]
    availableForInventory: (inventoryId: number) => InventoryItem[]
    sizeLabel: (req: RequiredInventoryItem, sizeId?: number | null) => string
    itemLabel: (item: InventoryItem, req: RequiredInventoryItem) => string
}

/** What the account shows about itself: its sessions, and its data export. */
export interface PitchTrust {
    sessions?: ActiveSession[]
    gdpr?: {managedMembers: {id: number; name: string}[]}
}

/** The notification settings of one member, and the personal feeds they can subscribe to. */
export interface PitchNotifications {
    settings?: UserSettings
    feeds?: {
        icon: [string, string]
        title: string
        helpRouteName: string
        hint: string
        url: string
        recommended?: boolean
        recommendedLabel?: string
    }[]
}

/** The capabilities a station has switched on for one partner, per direction. */
export interface PitchFederation {
    capabilities: {label: string; receive: boolean; send: boolean}[]
}

/** Moving a station to another instance: the two sections, or a running import. */
export interface PitchTransfer {
    progress?: {
        stationName: string
        status: 'IN_PROGRESS' | 'COMPLETED' | 'FAILED'
        phases: string[]
        completedPhases: number
        currentPhase: string | null
        subTotal: number
        subCompleted: number
        error: string | null
    }
}

/** The pages of a station: the tree in the management, and the rows of the page itself. */
export interface PitchPages {
    tree: {page: StationPage; depth: number}[]
    rows: PageRow[]
    landingPageId: number | null
}

/** A waiting list with everything its sections draw: the settings, the fields, the entries. */
export interface PitchWaitlist {
    list: WaitingList
    fields: WaitingListField[]
    groups: MemberGroup[]
    pending: WaitingListEntryWithScore[]
    waiting: WaitingListEntryWithScore[]
    testing: WaitingListEntryWithScore[]
    lists?: WaitingListWithCount[]
}

/** A form being filled in: its questions and the answers already given. */
export interface PitchForm {
    questions: FormQuestion[]
    answers: Record<number, Record<string, unknown>>
}

/** The evaluation of a form: the charts per question, and who has not answered yet. */
export interface PitchFormAnalytics {
    questions: FormQuestionAnalytics[]
    missing: MemberIdentity[]
}

/** A post as the news list draws it: the header, the prose, the comment count. */
export interface PitchNews {
    kind: 'local' | 'federated'
    id: number
    title: string
    contentHtml?: string
    author?: MemberIdentity | null
    authorName?: string
    publishedAt?: string
    restricted?: boolean
    publicBlog?: boolean
    stationName?: string
    stationUid?: string
    commentCount: number
}

/** What the editor panels of a post carry: its audience, its blog flag, its federation. */
export interface PitchNewsSettings {
    groups: MemberGroup[]
    tags: UserTag[]
    selectedUserTypes: string[]
    selectedGroupIds: number[]
    selectedTagIds: number[]
    publicBlog: boolean
    shared: boolean
    scope: string
    partnerIds: number[]
    visibilityRole: string
    partners: PartnerResponse[]
    canFederate: boolean
}

/** A running procedure: its people, its steps, and which steps still wait on another. */
export interface PitchProcedure {
    assignees: MemberIdentity[]
    items: ProcedureItem[]
    /** Per step id, the names of the steps it waits for. */
    blockedBy: Record<number, string[]>
}

/** The steps of a procedure template, with the dependencies between them. */
export interface PitchProcedureTemplate {
    items: ProcedureTemplateItem[]
    dependencies: Record<number, number[]>
    getItemById: (id: number) => ProcedureTemplateItem | undefined
}

/** What the application's own attendance summary and member list need to draw a session. */
export interface PitchAttendance {
    entries: AttendanceEntry[]
    members: StationMember[]
    sections: {group: MemberGroup | null; members: StationMember[]}[]
}

/** What the application's own grading panel needs: the section, its items, and what is ticked. */
export interface PitchGrading {
    section: TestProtocolSection
    childSections: TestProtocolSection[]
    sectionItems: (sectionId: number) => TestProtocolItem[]
    checks: Map<number, boolean>
    score: number
    maxPoints: number
    done: boolean
}

/** What the application's own evaluation table needs to draw a whole run. */
export interface PitchEvaluation {
    evalData: EvaluationResponse
    memberMap: Map<number, StationMember>
}

/** A section switch above the content, as the grading carries one per section of the sheet. */
export interface PitchTab {
    label: string
    /** The monospaced score behind the label. */
    score?: string
    /** Marked with the check the finished sections carry. */
    done?: boolean
    /** The section the screen is showing. Without it the first switch is the active one. */
    selected?: boolean
}

/** The progress line of a running session: the step on the left, the score on the right, a bar. */
export interface PitchProgress {
    label: string
    value: string
    percent: number
}

/** The bar above a list: the create action on the left, the view modes on the right. */
export interface PitchToolbar {
    create?: string
    views?: string[]
}

/** The filter row the lists carry: search, a select and a switch. */
export interface PitchFilters {
    search?: string
    select?: {label: string; value: string}
    toggle?: string
}

/** A dashboard tile: heading with icon and count, an action, and its entries. */
export interface PitchPanel {
    icon: string
    title: string
    count?: number
    action?: string
    entries: {title: string; meta: string}[]
}

/**
 * A preview of a real screen, drawn inside the station shell so it reads as the application
 * rather than as an illustration.
 */
export interface PitchScreen {
    /** The page title in the header, and what stands under it. */
    title: string
    subtitle?: string
    station?: string
    tabs?: (string | PitchTab)[]
    toolbar?: PitchToolbar
    filters?: PitchFilters
    /** The label-and-value line the detail views carry above their lists. */
    summary?: {label: string; value: string}[]
    /** The status counts the registration and attendance views show as badges. */
    badges?: {text: string; tone: Accent}[]
    /** A muted line of instruction above the content, as several screens carry. */
    hint?: string
    progress?: PitchProgress
    /** The catalogues the training offers, with the ones already picked. */
    catalogs?: {items: QuizCatalog[]; selected: Set<number>}
    /** The line the grading keeps above the sheet: the label left, the score right. */
    total?: {label: string; value: string}
    /** The counter row an inventory carries above its items. */
    stats?: PitchStats
    storage?: PitchStorage
    board?: PitchBoard
    /** Rendered by the application's own comment thread, read-only. */
    comments?: Comment[]
    inventoryCheck?: PitchInventoryCheck
    rapidCheck?: PitchRapidCheck
    attendance?: PitchAttendance
    forms?: Form[]
    federation?: PitchFederation
    notifications?: PitchNotifications
    trust?: PitchTrust
    transfer?: PitchTransfer
    /** The public pages, with the side a screen shows: the tree or the page. */
    pages?: {data: PitchPages; section: 'tree' | 'page'}
    /** The waiting list, with the section a screen shows of it. */
    waitlist?: {data: PitchWaitlist; section: 'lists' | 'pending' | 'waiting' | 'testing' | 'settings'}
    form?: PitchForm
    formAnalytics?: PitchFormAnalytics
    news?: PitchNews[]
    newsSettings?: PitchNewsSettings
    procedure?: PitchProcedure
    procedureTemplate?: PitchProcedureTemplate
    check?: PitchCheckMode
    grading?: PitchGrading
    evaluation?: PitchEvaluation
    table?: PitchTable
    rows?: PitchListRow[]
    tiles?: KbItem[]
    panels?: PitchPanel[]
    /** The path line the wiki shows above its contents. */
    breadcrumb?: string[]
    /** A section heading above the rows, as the lists group their entries. */
    section?: string
    /** Buttons the page offers, drawn as the application draws them. */
    actions?: string[]
    footer?: string
}

export interface PitchMetric {
    value: string
    label: string
    accent: Accent
}

export interface PitchColumn {
    title: string
    items: string[]
}

export interface PitchRoadmapColumn {
    label: string
    accent: Accent
    items: string[]
    note?: string
}

interface SlideBase {
    id: string
    accent: Accent
    /**
     * The slide's ground. Both follow the active theme - `accent` is the slightly lifted surface
     * used to break up a run of slides, not a dark variant.
     */
    tone?: 'plain' | 'accent'
    chip?: string
    heading: string
    /** Rendered in the accent colour, appended to the heading. */
    headingAccent?: string
    lead?: string
    note?: string
}

export interface CoverSlide extends SlideBase {
    kind: 'cover'
    pills: string[]
    cards?: PitchCard[]
}

export interface CardsSlide extends SlideBase {
    kind: 'cards'
    cards: PitchCard[]
    columns?: 2 | 3 | 4
    metrics?: PitchMetric[]
}

export interface SplitSlide extends SlideBase {
    kind: 'split'
    bullets: string[]
    screen: PitchScreen
    /**
     * Puts the screen below the bullets at full width. Needed whenever the screen lays its content
     * out in a grid: those grids follow the width of the window, not of the panel they sit in.
     */
    wide?: boolean
    /** Shown above the heading as "Modul 3 / 18". */
    counter?: string
}

/** A concrete look at the running application: one or two screens with what they do. */
export interface ShowcaseSlide extends SlideBase {
    kind: 'showcase'
    screens: PitchScreen[]
    points: string[]
}

export interface ColumnsSlide extends SlideBase {
    kind: 'columns'
    groups: PitchColumn[]
}

export interface RoadmapSlide extends SlideBase {
    kind: 'roadmap'
    columns: PitchRoadmapColumn[]
}

export type PitchSlideData =
    | CoverSlide
    | CardsSlide
    | SplitSlide
    | ShowcaseSlide
    | ColumnsSlide
    | RoadmapSlide

/**
 * One column of the deck: the slide the horizontal arrows reach, and the slides below it that go
 * into detail on what it claims.
 */
export interface PitchTrack {
    overview: PitchSlideData
    details: ShowcaseSlide[]
}
