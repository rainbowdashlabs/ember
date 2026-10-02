/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import { createCrudResource, createScopedCrudResource, type NoContent } from './crud'
import { uploadFile } from './upload'
import type { CommentSource } from './comments'
import { FieldType, StationPermission } from './generated/schema'
import { downloadAuthed } from '@/util/downloadAuthed'
import { isYes } from '@/util/questions'
import { OfferedFieldTypes } from './fieldTypes'
import type {
    AccessData,
    AccessRequest,
    Board,
    BoardActivityEntry,
    BoardChecklistItem,
    BoardFieldConfig,
    BoardFieldDefinition,
    BoardFieldValue,
    BoardLabel,
    BooleanValue,
    DateValue,
    Enum,
    EnumValue,
    LaneAssignee,
    LaneAssigneeValue,
    NumberValue,
    Simple,
    StringValue,
    BoardLane,
    BoardTicket,
    BoardTicketAssignRequest,
    BoardTicketAttachment,
    BoardTicketCommentRequest,
    BoardTicketFieldValue,
    BoardTicketHistoryResponse,
    BoardTicketKbLink,
    BoardTicketLink,
    BoardTicketTransitionResponse,
    BoardWeblink,
    CanEditResponse,
    ChecklistItemRequest,
    CommentResponse,
    components,
    CreateBoardRequest,
    CreateTicketRequest,
    FederationConfigRequest,
    FederationConfigResponse,
    FieldRequest,
    LabelRequest,
    LaneRequest,
    LinkRequest,
    MemberCompletion,
    MoveTicketRequest,
    ReorderChecklistRequest,
    RemoteBoard,
    ReorderRequest,
    TicketLabelMapping,
    TicketSummary,
    UpdateBoardRequest,
    UpdateTicketRequest,
    WeblinkRequest,
} from './generated/schema'

type Schemas = components['schemas']

export type TicketPriorityName = Schemas['TicketPriority']

export const TicketPriority = {
    LOWEST: 'LOWEST',
    LOW: 'LOW',
    MEDIUM: 'MEDIUM',
    HIGH: 'HIGH',
    HIGHEST: 'HIGHEST',
} as const satisfies Record<TicketPriorityName, TicketPriorityName>

export type LinkTypeName = Schemas['LinkType']

export const LinkType = {
    RELATES_TO: 'RELATES_TO',
    BLOCKS: 'BLOCKS',
    BLOCKED_BY: 'BLOCKED_BY',
    CAUSES: 'CAUSES',
    CAUSED_BY: 'CAUSED_BY',
} as const satisfies Record<LinkTypeName, LinkTypeName>

export type LanePresetName = Schemas['LanePreset']

export const LanePreset = {
    SIMPLE: 'SIMPLE',
    FEEDBACK: 'FEEDBACK',
} as const satisfies Record<LanePresetName, LanePresetName>

/** The field types a board offers. */
export type BoardFieldTypeName = (typeof OfferedFieldTypes.BOARD)[number]

/** Whether a value a picker hands back names a field type a board offers. */
export function isBoardFieldType(value: unknown): value is BoardFieldTypeName {
    return OfferedFieldTypes.BOARD.some(type => type === value)
}

/**
 * The settings record each board field type keeps, as the server binds them. Written down here
 * because the shared type names do not say which record a board's settings are.
 */
type BoardFieldConfigByType = {
    TEXT: Simple
    NUMBER: Simple
    BOOLEAN: Simple
    DATE: Simple
    CHOICE: Enum
    LANE_ASSIGNEE: LaneAssignee
}

/** The value record each board field type keeps, as the server binds them. */
type BoardFieldValueByType = {
    TEXT: StringValue
    NUMBER: NumberValue
    BOOLEAN: BooleanValue
    DATE: DateValue
    CHOICE: EnumValue
    LANE_ASSIGNEE: LaneAssigneeValue
}

/** A board as its own station sends it, or as a partner's board arrives through federation. */
export type AnyBoard = Board | RemoteBoard

/**
 * A board field whose settings are the record its type names, so a reader narrowing on `fieldType`
 * holds the settings of exactly that type.
 */
export type TypedBoardField = {
    [K in BoardFieldTypeName]: Omit<BoardFieldDefinition, 'fieldType' | 'config'> & {fieldType: K; config: BoardFieldConfigByType[K]}
}[BoardFieldTypeName]

/** A ticket's value of one field, as the record the field's type names. */
export type TypedBoardFieldValue = {
    [K in BoardFieldTypeName]: Omit<BoardTicketFieldValue, 'fieldType' | 'value'> & {fieldType: K; value: BoardFieldValueByType[K] | null}
}[BoardFieldTypeName]

/**
 * The fields of a board with each one's settings typed by its field type.
 *
 * <p>The server binds a field's settings, and a ticket's value of it, by the field type standing next
 * to them, so every field it sends pairs the two; the generated union of every settings record cannot
 * say which one a field holds. This and {@link typedFieldValues} are the one place that reads the
 * pairing.
 */
export function typedFields(fields: BoardFieldDefinition[]): TypedBoardField[] {
    return fields as TypedBoardField[]
}

/** A ticket's field values with each value typed by its field type, paired as {@link typedFields} says. */
export function typedFieldValues(values: BoardTicketFieldValue[]): TypedBoardFieldValue[] {
    return values as TypedBoardFieldValue[]
}

/** A field value as the ticket screen edits it: the text, number, switch or member id itself. */
export type BoardFieldRaw = string | number | boolean

/** The bare value a ticket holds for a field, or null where it holds none. */
export function rawFieldValue(entry: TypedBoardFieldValue): BoardFieldRaw | null {
    if (entry.value === null) return null
    if (entry.fieldType === FieldType.LANE_ASSIGNEE) return entry.value.memberId
    return entry.value.value
}

/** The value record the server binds for a field of the given type, built from what the screen holds. */
export function fieldValueBody(fieldType: BoardFieldTypeName, raw: BoardFieldRaw): BoardFieldValue {
    if (fieldType === FieldType.LANE_ASSIGNEE) return {memberId: Number(raw)}
    return {value: raw}
}

/**
 * What a ticket holds for a field, as the text every answer box reads: a yes as {@code true}, a
 * number as its digits, a member as their id, and nothing as an empty answer.
 */
export function fieldValueText(raw: BoardFieldRaw | null | undefined): string {
    return raw === null || raw === undefined ? '' : String(raw)
}

/**
 * The text an answer box hands back as what a ticket holds for a field of this type, or null where
 * it holds nothing. A zero stays a zero.
 */
export function fieldValueOfText(fieldType: BoardFieldTypeName, text: string): BoardFieldRaw | null {
    if (text === '') return null
    if (fieldType === FieldType.BOOLEAN) return isYes(text)
    if (fieldType === FieldType.NUMBER || fieldType === FieldType.LANE_ASSIGNEE) {
        const number = Number(text)
        return Number.isNaN(number) ? null : number
    }
    return text
}

/**
 * One field as the board settings edit it, whatever its type: the type can change while it is
 * edited, so the settings every type might need are held side by side and only the ones the chosen
 * type keeps are sent.
 */
export interface BoardFieldDraft {
    name: string
    fieldType: BoardFieldTypeName
    required: boolean
    options: string[]
    laneId: number | null
}

/** A field the board already has, as the settings edit it. */
export function fieldDraftOf(field: TypedBoardField): BoardFieldDraft {
    const draft: BoardFieldDraft = {
        name: field.name,
        fieldType: field.fieldType,
        required: field.config.required,
        options: [],
        laneId: null,
    }
    if (field.fieldType === FieldType.CHOICE) return {...draft, options: field.config.options}
    if (field.fieldType === FieldType.LANE_ASSIGNEE) return {...draft, laneId: field.config.laneId}
    return draft
}

/**
 * The settings the draft's type keeps. A lane assignee without a lane is sent without one, which the
 * server reads as that type's empty settings.
 */
function fieldConfigOf(draft: BoardFieldDraft): BoardFieldConfig {
    if (draft.fieldType === FieldType.CHOICE) return {required: draft.required, options: draft.options}
    if (draft.fieldType === FieldType.LANE_ASSIGNEE && draft.laneId !== null) {
        return {required: draft.required, laneId: draft.laneId}
    }
    return {required: draft.required}
}

const boards = createCrudResource<
    Board,
    CreateBoardRequest,
    UpdateBoardRequest,
    Board,
    Board,
    Board,
    string
>('/boards')

export async function listBoards(visibleOnly?: boolean): Promise<Board[]> {
    return boards.list(visibleOnly ? { visible: 'true' } : undefined)
}

export const getBoard = boards.get
export const createBoard = boards.create
export const updateBoard = boards.update
export const deleteBoard = boards.remove

export async function canEditBoard(boardKey: string): Promise<boolean> {
    const res = await client.get<CanEditResponse>(`/boards/${boardKey}/can-edit`)
    return res.data.canEdit
}

export async function getBoardMembers(boardKey: string): Promise<MemberCompletion[]> {
    const res = await client.get<MemberCompletion[]>(`/boards/${boardKey}/members`)
    return res.data
}

/**
 * Whom a ticket on this board may be handed to, which is narrower than the station's members. The
 * full list stays in use for reading names off tickets and comments, so somebody who was assigned
 * a ticket before losing their write access keeps their name on it.
 */
export async function getAssignableMembers(boardKey: string): Promise<MemberCompletion[]> {
    const res = await client.get<MemberCompletion[]>(`/boards/${boardKey}/assignable-members`)
    return res.data
}

export async function getLanes(boardKey: string): Promise<BoardLane[]> {
    const res = await client.get<BoardLane[]>(`/boards/${boardKey}/lanes`)
    return res.data
}

export async function setLanes(boardKey: string, lanes: LaneRequest[]): Promise<BoardLane[]> {
    const res = await client.put<BoardLane[]>(`/boards/${boardKey}/lanes`, lanes)
    return res.data
}

const labels = createScopedCrudResource<
    BoardLabel,
    LabelRequest,
    LabelRequest,
    BoardLabel,
    BoardLabel,
    NoContent,
    number,
    string
>((boardKey: string) => `/boards/${boardKey}/labels`)

export const getLabels = labels.list
export const createLabel = labels.create
export const updateLabel = labels.update
export const deleteLabel = labels.remove

export async function getTicketLabels(boardKey: string, ticketNumber: number): Promise<BoardLabel[]> {
    const res = await client.get<BoardLabel[]>(`/boards/${boardKey}/tickets/${ticketNumber}/labels`)
    return res.data
}

export async function addTicketLabel(boardKey: string, ticketNumber: number, labelId: number): Promise<BoardLabel[]> {
    const res = await client.post<BoardLabel[]>(`/boards/${boardKey}/tickets/${ticketNumber}/labels/${labelId}`)
    return res.data
}

export async function removeTicketLabel(boardKey: string, ticketNumber: number, labelId: number): Promise<void> {
    await client.delete(`/boards/${boardKey}/tickets/${ticketNumber}/labels/${labelId}`)
}

export async function getAllTicketLabels(boardKey: string): Promise<TicketLabelMapping[]> {
    const res = await client.get<TicketLabelMapping[]>(`/boards/${boardKey}/ticket-labels`)
    return res.data
}

export async function enableBacklog(boardKey: string): Promise<BoardLane> {
    const res = await client.post<BoardLane>(`/boards/${boardKey}/backlog`)
    return res.data
}

export async function disableBacklog(boardKey: string): Promise<void> {
    await client.delete(`/boards/${boardKey}/backlog`)
}

export async function getFields(boardKey: string): Promise<TypedBoardField[]> {
    const res = await client.get<BoardFieldDefinition[]>(`/boards/${boardKey}/fields`)
    return typedFields(res.data)
}

/** Replaces the board's fields by the drafts, in their order, each with the settings its type keeps. */
export async function setFields(boardKey: string, drafts: BoardFieldDraft[]): Promise<TypedBoardField[]> {
    const body: FieldRequest[] = drafts.map(draft => ({name: draft.name, fieldType: draft.fieldType, config: fieldConfigOf(draft)}))
    const res = await client.put<BoardFieldDefinition[]>(`/boards/${boardKey}/fields`, body)
    return typedFields(res.data)
}

export async function getBoardFederationConfig(boardKey: string): Promise<FederationConfigResponse> {
    const res = await client.get<FederationConfigResponse>(`/boards/${boardKey}/federation`)
    return res.data
}

export async function setBoardFederationConfig(boardKey: string, data: FederationConfigRequest): Promise<void> {
    await client.put(`/boards/${boardKey}/federation`, data)
}

export async function getViewAccess(boardKey: string): Promise<AccessData> {
    const res = await client.get<AccessData>(`/boards/${boardKey}/access/view`)
    return res.data
}

export async function setViewAccess(boardKey: string, data: AccessRequest): Promise<void> {
    await client.put(`/boards/${boardKey}/access/view`, data)
}

export async function getEditAccess(boardKey: string): Promise<AccessData> {
    const res = await client.get<AccessData>(`/boards/${boardKey}/access/edit`)
    return res.data
}

export async function setEditAccess(boardKey: string, data: AccessRequest): Promise<void> {
    await client.put(`/boards/${boardKey}/access/edit`, data)
}

export async function searchTickets(boardKey: string, query: string): Promise<TicketSummary[]> {
    const res = await client.get<TicketSummary[]>(`/boards/${boardKey}/tickets/search`, {
        params: { q: query },
    })
    return res.data
}

const tickets = createScopedCrudResource<
    TicketSummary,
    CreateTicketRequest,
    UpdateTicketRequest,
    BoardTicket,
    BoardTicket,
    BoardTicket,
    number,
    string
>((boardKey: string) => `/boards/${boardKey}/tickets`)

export const listTickets = tickets.list
export const getTicket = tickets.get
export const createTicket = tickets.create
export const updateTicket = tickets.update
export const deleteTicket = tickets.remove

/** Hands the ticket to the member, or takes it off whoever holds it where no member is given. */
export async function assignTicket(boardKey: string, ticketNumber: number, assignedMemberId: number | null): Promise<BoardTicket> {
    const res = await client.put<BoardTicket>(`/boards/${boardKey}/tickets/${ticketNumber}/assign`, {
        assignedMemberId,
    } satisfies BoardTicketAssignRequest)
    return res.data
}

export async function moveTicket(boardKey: string, ticketNumber: number, data: MoveTicketRequest): Promise<BoardTicket> {
    const res = await client.put<BoardTicket>(`/boards/${boardKey}/tickets/${ticketNumber}/move`, data)
    return res.data
}

export async function reorderTickets(boardKey: string, ticketNumber: number, data: ReorderRequest): Promise<void> {
    await client.put(`/boards/${boardKey}/tickets/${ticketNumber}/reorder`, data)
}

export async function getLinks(boardKey: string, ticketNumber: number): Promise<BoardTicketLink[]> {
    const res = await client.get<BoardTicketLink[]>(`/boards/${boardKey}/tickets/${ticketNumber}/links`)
    return res.data
}

export async function createLink(boardKey: string, ticketNumber: number, data: LinkRequest): Promise<BoardTicketLink[]> {
    const res = await client.post<BoardTicketLink[]>(`/boards/${boardKey}/tickets/${ticketNumber}/links`, data)
    return res.data
}

export async function deleteLink(boardKey: string, ticketNumber: number, linkedId: number): Promise<void> {
    await client.delete(`/boards/${boardKey}/tickets/${ticketNumber}/links/${linkedId}`)
}

export async function getChecklist(boardKey: string, ticketNumber: number): Promise<BoardChecklistItem[]> {
    const res = await client.get<BoardChecklistItem[]>(`/boards/${boardKey}/tickets/${ticketNumber}/checklist`)
    return res.data
}

export async function addChecklistItem(boardKey: string, ticketNumber: number, data: ChecklistItemRequest): Promise<BoardChecklistItem> {
    const res = await client.post<BoardChecklistItem>(`/boards/${boardKey}/tickets/${ticketNumber}/checklist`, data)
    return res.data
}

export async function updateChecklistItem(boardKey: string, ticketNumber: number, itemId: number, data: ChecklistItemRequest): Promise<void> {
    await client.put(`/boards/${boardKey}/tickets/${ticketNumber}/checklist/${itemId}`, data)
}

export async function deleteChecklistItem(boardKey: string, ticketNumber: number, itemId: number): Promise<void> {
    await client.delete(`/boards/${boardKey}/tickets/${ticketNumber}/checklist/${itemId}`)
}

export async function reorderChecklist(boardKey: string, ticketNumber: number, data: ReorderChecklistRequest): Promise<void> {
    await client.put(`/boards/${boardKey}/tickets/${ticketNumber}/checklist/reorder`, data)
}

export async function getTransitions(boardKey: string, ticketNumber: number): Promise<BoardTicketTransitionResponse[]> {
    const res = await client.get<BoardTicketTransitionResponse[]>(`/boards/${boardKey}/tickets/${ticketNumber}/transitions`)
    return res.data
}

/**
 * The thread under a ticket of the station's own boards. A mention offers the board's members, and a
 * board manager removes anybody's comment there.
 */
export function ticketCommentSource(boardKey: string, ticketNumber: number): CommentSource {
    const base = `/boards/${boardKey}/tickets/${ticketNumber}/comments`
    return {
        list: async () => (await client.get<CommentResponse[]>(base)).data,
        create: (parentId, content) => client.post(base, {parentId: parentId ?? undefined, content} satisfies BoardTicketCommentRequest),
        update: (commentId, content) => client.put(`${base}/${commentId}`, {content} satisfies BoardTicketCommentRequest),
        remove: commentId => client.delete(`${base}/${commentId}`),
        mentionables: async () => ({members: await getBoardMembers(boardKey), groups: []}),
        moderator: StationPermission.BOARD_MANAGER,
    }
}

export async function getWeblinks(boardKey: string, ticketNumber: number): Promise<BoardWeblink[]> {
    const res = await client.get<BoardWeblink[]>(`/boards/${boardKey}/tickets/${ticketNumber}/weblinks`)
    return res.data
}

export async function addWeblink(boardKey: string, ticketNumber: number, data: WeblinkRequest): Promise<BoardWeblink> {
    const res = await client.post<BoardWeblink>(`/boards/${boardKey}/tickets/${ticketNumber}/weblinks`, data)
    return res.data
}

export async function deleteWeblink(boardKey: string, ticketNumber: number, weblinkId: number): Promise<void> {
    await client.delete(`/boards/${boardKey}/tickets/${ticketNumber}/weblinks/${weblinkId}`)
}

export async function getAttachments(boardKey: string, ticketNumber: number): Promise<BoardTicketAttachment[]> {
    const res = await client.get<BoardTicketAttachment[]>(`/boards/${boardKey}/tickets/${ticketNumber}/attachments`)
    return res.data
}

export async function uploadAttachment(boardKey: string, ticketNumber: number, file: File): Promise<BoardTicketAttachment> {
    return uploadFile<BoardTicketAttachment>(`/boards/${boardKey}/tickets/${ticketNumber}/attachments`, { file })
}

export async function downloadAttachmentBlob(boardKey: string, ticketNumber: number, attachmentId: number, filename: string): Promise<void> {
    await downloadAuthed(`/boards/${boardKey}/tickets/${ticketNumber}/attachments/${attachmentId}/download`, filename)
}

export async function getAttachmentBlobUrl(boardKey: string, ticketNumber: number, attachmentId: number): Promise<string> {
    const res = await client.get<Blob>(`/boards/${boardKey}/tickets/${ticketNumber}/attachments/${attachmentId}/download`, { responseType: 'blob' })
    return URL.createObjectURL(res.data)
}

export async function getAttachmentText(boardKey: string, ticketNumber: number, attachmentId: number): Promise<string> {
    const res = await client.get<string>(`/boards/${boardKey}/tickets/${ticketNumber}/attachments/${attachmentId}/download`, { responseType: 'text' })
    return res.data
}

export async function deleteAttachment(boardKey: string, ticketNumber: number, attachmentId: number): Promise<void> {
    await client.delete(`/boards/${boardKey}/tickets/${ticketNumber}/attachments/${attachmentId}`)
}

export async function getWatchers(boardKey: string, ticketNumber: number): Promise<number[]> {
    const res = await client.get<number[]>(`/boards/${boardKey}/tickets/${ticketNumber}/watchers`)
    return res.data
}

export async function watchTicket(boardKey: string, ticketNumber: number): Promise<void> {
    await client.post(`/boards/${boardKey}/tickets/${ticketNumber}/watch`)
}

export async function unwatchTicket(boardKey: string, ticketNumber: number): Promise<void> {
    await client.delete(`/boards/${boardKey}/tickets/${ticketNumber}/watch`)
}

export async function getKbLinks(boardKey: string, ticketNumber: number): Promise<BoardTicketKbLink[]> {
    const res = await client.get<BoardTicketKbLink[]>(`/boards/${boardKey}/tickets/${ticketNumber}/kb-links`)
    return res.data
}

export async function addKbLink(boardKey: string, ticketNumber: number, kbFileId: number): Promise<void> {
    await client.post(`/boards/${boardKey}/tickets/${ticketNumber}/kb-links/${kbFileId}`)
}

export async function removeKbLink(boardKey: string, ticketNumber: number, linkId: number): Promise<void> {
    await client.delete(`/boards/${boardKey}/tickets/${ticketNumber}/kb-links/${linkId}`)
}

export async function getHistory(boardKey: string, ticketNumber: number): Promise<BoardTicketHistoryResponse[]> {
    const res = await client.get<BoardTicketHistoryResponse[]>(`/boards/${boardKey}/tickets/${ticketNumber}/history`)
    return res.data
}

export async function getFieldValues(boardKey: string, ticketNumber: number): Promise<TypedBoardFieldValue[]> {
    const res = await client.get<BoardTicketFieldValue[]>(`/boards/${boardKey}/tickets/${ticketNumber}/fields`)
    return typedFieldValues(res.data)
}

/** Sets the ticket's value of a field, sent as the value record the field's type names. */
export async function setFieldValue(
    boardKey: string,
    ticketNumber: number,
    fieldId: number,
    fieldType: BoardFieldTypeName,
    raw: BoardFieldRaw,
): Promise<void> {
    await client.put(`/boards/${boardKey}/tickets/${ticketNumber}/fields/${fieldId}`, fieldValueBody(fieldType, raw))
}

export async function deleteFieldValue(boardKey: string, ticketNumber: number, fieldId: number): Promise<void> {
    await client.delete(`/boards/${boardKey}/tickets/${ticketNumber}/fields/${fieldId}`)
}

export async function getActivity(boardKey: string, ticketNumber: number): Promise<BoardActivityEntry[]> {
    const res = await client.get<BoardActivityEntry[]>(`/boards/${boardKey}/tickets/${ticketNumber}/activity`)
    return res.data
}
