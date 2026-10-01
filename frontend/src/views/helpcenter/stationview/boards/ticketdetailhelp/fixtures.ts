/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {noMentionables, type CommentSource} from '@/api/comments'
import {TicketPriority, type BoardFieldRaw, type TicketPriorityName, type TypedBoardField} from '@/api/boards'
import type {
    Board, BoardChecklistItem, BoardLabel, BoardLane, BoardTicket, BoardTicketAttachment, BoardTicketHistoryResponse,
    BoardTicketKbLink, BoardTicketLink, BoardTicketTransitionResponse, BoardWeblink, CommentResponse, MemberCompletion,
    MemberIdentity, TicketSummary,
} from '@/api/generated/schema'
import {priorityColor, priorityIcon, priorityOptions} from '@/util/ticketPriority'
import type {PriorityOption} from '@/views/stationview/boards/ticketdetailview/types'

type Translate = (key: string) => string

interface People {
    max: MemberIdentity
    lisa: MemberIdentity
}

const STATION_UID = 'help-station'
const BOARD_ID = 1
const TICKET_ID = 42

function identity(memberUid: string, name: string): MemberIdentity {
    return {stationUid: STATION_UID, memberUid, name, nameColor: null, stationName: null, displayTag: null}
}

function member(id: number, person: MemberIdentity): MemberCompletion {
    return {...person, id, name: person.name ?? ''}
}

function summary(id: number, title: string, laneId: number, priority: TicketPriorityName): TicketSummary {
    return {
        id, ticketNumber: id, title, laneId, priority, boardId: BOARD_ID, position: id, assignee: null, dueDate: null,
        attachmentCount: 0, checklistChecked: 0, checklistTotal: 0, laneEnteredAt: '2026-06-01T09:00:00Z',
    }
}

function attachment(id: number, originalName: string, contentType: string, sizeBytes: number, uploader: MemberIdentity): BoardTicketAttachment {
    return {
        id, ticketId: TICKET_ID, filename: `${id}`, originalName, contentType, sizeBytes, uploaderMemberUid: uploader.memberUid,
        uploaderStationUid: STATION_UID, createdAt: '2026-06-03T10:00:00Z',
    }
}

function boardFixtures(t: Translate) {
    const board: Board = {
        id: BOARD_ID, uid: 'help-board', stationId: STATION_UID, shortKey: 'JF', name: t('helpCenter.sample.boards.boardName'),
        description: null, backlogLaneId: null, hideDoneAfterDays: 14, ticketCounter: 42, createdAt: '2026-01-10T09:00:00Z',
    }
    const lanes: BoardLane[] = [
        {id: 1, boardId: BOARD_ID, name: t('helpCenter.ticketCreate.laneToDo'), color: '#6b7280', position: 0},
        {id: 2, boardId: BOARD_ID, name: t('helpCenter.ticketDetail.laneInProgress'), color: '#3694FF', position: 1},
        {id: 3, boardId: BOARD_ID, name: t('helpCenter.sample.boards.done'), color: '#22c55e', position: 2},
    ]
    const allLabels: BoardLabel[] = [
        {id: 1, boardId: BOARD_ID, name: t('helpCenter.sample.boards.urgent'), color: '#ec2929'},
        {id: 2, boardId: BOARD_ID, name: t('helpCenter.sample.boards.training'), color: '#3694FF'},
    ]
    const boardFields: TypedBoardField[] = [
        {id: 1, boardId: BOARD_ID, position: 0, name: t('helpCenter.ticketDetail.customFieldExample'), fieldType: 'NUMBER', config: {required: false}},
    ]
    const priorityChoices: PriorityOption[] = priorityOptions(t).reverse().map(option => {
        const value = option.value as TicketPriorityName
        return {...option, icon: priorityIcon(value), color: priorityColor(value)}
    })
    return {board, lanes, allLabels, boardFields, priorityChoices}
}

function ticketFixture(t: Translate, {max, lisa}: People): BoardTicket {
    return {
        id: TICKET_ID, boardId: BOARD_ID, ticketNumber: 42, laneId: 2, position: 0, priority: TicketPriority.HIGH,
        title: t('helpCenter.ticketDetail.dummyTitle'), description: t('helpCenter.ticketDetail.dummyDescription'),
        assignee: max, creator: lisa, dueDate: '2026-06-20', attachmentCount: 2, checklistChecked: 1, checklistTotal: 3,
        createdAt: '2026-06-01T09:00:00Z', updatedAt: '2026-06-05T15:10:00Z', laneEnteredAt: '2026-06-02T08:00:00Z',
    }
}

function contentFixtures(t: Translate, {max, lisa}: People) {
    const allTickets: TicketSummary[] = [
        summary(15, t('helpCenter.ticketDetail.linkedTicket1'), 1, TicketPriority.MEDIUM),
        summary(38, t('helpCenter.ticketDetail.linkedTicket2'), 3, TicketPriority.LOW),
    ]
    const links: BoardTicketLink[] = [
        {ticketId: TICKET_ID, linkedTicketId: 15, linkType: 'BLOCKS'},
        {ticketId: TICKET_ID, linkedTicketId: 38, linkType: 'RELATES_TO'},
    ]
    const checklist: BoardChecklistItem[] = ['checkItem1', 'checkItem2', 'checkItem3'].map((key, index) => ({
        id: index + 1, ticketId: TICKET_ID, position: index, checked: index === 0, title: t(`helpCenter.ticketDetail.${key}`),
    }))
    const weblinks: BoardWeblink[] = [
        {id: 1, ticketId: TICKET_ID, position: 0, url: 'https://example.org/bestellliste', title: t('helpCenter.ticketDetail.weblinkExample')},
    ]
    const attachments = [
        attachment(1, t('helpCenter.ticketDetail.attachmentExample'), 'text/csv', 2_400, lisa),
        attachment(2, t('helpCenter.ticketDetail.attachmentPdf'), 'application/pdf', 184_000, max),
    ]
    const kbLinks: BoardTicketKbLink[] = [
        {id: 1, ticketId: TICKET_ID, kbFileId: 7, title: t('helpCenter.ticketDetail.kbLinkExample'), folderPath: ''},
    ]
    return {allTickets, links, checklist, weblinks, attachments, kbLinks}
}

/** A comment thread that only reads its fixtures back, so the help never writes a comment anywhere. */
function fixedCommentSource(comments: CommentResponse[]): CommentSource {
    return {
        list: () => Promise.resolve(comments),
        create: () => Promise.resolve(),
        update: () => Promise.resolve(),
        remove: () => Promise.resolve(),
        mentionables: noMentionables,
        moderator: null,
    }
}

function activityFixtures(t: Translate, {max, lisa}: People) {
    const transitions: BoardTicketTransitionResponse[] = [
        {id: 1, ticketId: TICKET_ID, actor: lisa, actorName: lisa.name, fromLaneId: 1, toLaneId: 2, movedAt: '2026-06-02T08:00:00Z'},
    ]
    const history: BoardTicketHistoryResponse[] = [
        {id: 1, ticketId: TICKET_ID, actor: max, actorName: max.name, action: 'PRIORITY_CHANGED', detail: 'MEDIUM → HIGH', createdAt: '2026-06-02T08:30:00Z'},
    ]
    const comments: CommentResponse[] = [
        {id: 1, ticketId: TICKET_ID, author: lisa, content: t('helpCenter.ticketDetail.commentExample1'), deleted: false, createdAt: '2026-06-05T12:30:00Z'},
        {
            id: 2, ticketId: TICKET_ID, parentId: 1, author: max, content: t('helpCenter.ticketDetail.commentReply1'), deleted: false,
            createdAt: '2026-06-05T13:10:00Z', updatedAt: '2026-06-05T13:20:00Z',
        },
    ]
    return {transitions, history, commentSource: fixedCommentSource(comments)}
}

/** Every fixture the ticket page shows in the help, in the shapes the real ticket page receives them. */
export function ticketDetailFixtures(t: Translate) {
    const people: People = {
        max: identity('help-max', t('helpCenter.sample.people.maxMustermann')),
        lisa: identity('help-lisa', t('helpCenter.sample.people.lisaSchmidt')),
    }
    const members = [member(1, people.max), member(2, people.lisa)]
    const boardParts = boardFixtures(t)
    const fieldValues: Record<number, BoardFieldRaw | null> = {1: 3}
    return {
        ...boardParts,
        ...contentFixtures(t, people),
        ...activityFixtures(t, people),
        ticket: ticketFixture(t, people),
        ticketLabels: [boardParts.allLabels[0]!],
        members,
        assignedMemberId: String(members[0]!.id),
        fieldValues,
    }
}
