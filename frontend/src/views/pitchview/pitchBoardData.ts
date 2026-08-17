/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Comment} from '@/api/comments'
import type {MemberCompletion} from '@/api/stationMembers'
import type {MemberIdentity} from '@/api/types'
import type {PitchBoard} from './pitchTypes'

/**
 * The board a demonstration shows. It is fed to the application's own board components, so every
 * field here is the field the application really reads - the time dots, for one, are counted from
 * {@link laneEnteredAt} and therefore have to move with the calendar.
 */
function daysAgo(days: number): string {
    const date = new Date()
    date.setDate(date.getDate() - days)
    return date.toISOString()
}

function inDays(days: number): string {
    const date = new Date()
    date.setDate(date.getDate() + days)
    return date.toISOString().slice(0, 10)
}

function identity(name: string, uid: string): MemberIdentity {
    return {stationUid: 'wache', memberUid: uid, name}
}

const ANNA = identity('Anna Müller', 'm-anna')
const BEN = identity('Ben Krüger', 'm-ben')
const CLARA = identity('Clara Weiß', 'm-clara')

const MEMBERS: MemberCompletion[] = [ANNA, BEN, CLARA].map((member, index) => ({
    id: index + 1,
    name: member.name ?? '',
    stationUid: 'wache',
    memberUid: member.memberUid ?? '',
}))

const MATERIAL = {id: 1, boardId: 1, name: 'Material', color: '#3694FF'}
const FAHRT = {id: 2, boardId: 1, name: 'Fahrt', color: '#00C507'}

export const CAMP_BOARD: PitchBoard = {
    shortKey: 'ZL',
    members: MEMBERS,
    archivedCount: 12,
    lanes: [
        {id: 1, boardId: 1, name: 'Offen', color: '#6b7280', position: 0},
        {id: 2, boardId: 1, name: 'In Arbeit', color: '#FF6421', position: 1},
        {id: 3, boardId: 1, name: 'Feedback', color: '#3694FF', position: 2},
        {id: 4, boardId: 1, name: 'Erledigt', color: '#00C507', position: 3},
    ],
    labels: {
        41: [MATERIAL],
        43: [MATERIAL],
        44: [FAHRT],
    },
    tickets: [
        {
            id: 44, boardId: 1, laneId: 1, ticketNumber: 44, title: 'Buskosten anfragen',
            assignee: CLARA, priority: 'LOW', dueDate: null, position: 0,
            laneEnteredAt: daysAgo(1), checklistTotal: 0, checklistChecked: 0, attachmentCount: 0,
        },
        {
            id: 43, boardId: 1, laneId: 1, ticketNumber: 43, title: 'Materialliste ergänzen',
            assignee: null, priority: 'MEDIUM', dueDate: null, position: 1,
            laneEnteredAt: daysAgo(4), checklistTotal: 0, checklistChecked: 0, attachmentCount: 0,
        },
        {
            id: 41, boardId: 1, laneId: 2, ticketNumber: 41, title: 'Zelte auf Schäden prüfen',
            assignee: BEN, priority: 'HIGH', dueDate: inDays(3), position: 0,
            laneEnteredAt: daysAgo(11), checklistTotal: 5, checklistChecked: 3, attachmentCount: 2,
        },
        {
            id: 38, boardId: 1, laneId: 2, ticketNumber: 38, title: 'Essen planen',
            assignee: ANNA, priority: 'MEDIUM', dueDate: inDays(13), position: 1,
            laneEnteredAt: daysAgo(6), checklistTotal: 4, checklistChecked: 1, attachmentCount: 0,
        },
        {
            id: 36, boardId: 1, laneId: 3, ticketNumber: 36, title: 'Elternbrief abstimmen',
            assignee: CLARA, priority: 'MEDIUM', dueDate: inDays(-2), position: 0,
            laneEnteredAt: daysAgo(24), checklistTotal: 0, checklistChecked: 0, attachmentCount: 1,
        },
        {
            id: 35, boardId: 1, laneId: 4, ticketNumber: 35, title: 'Bus buchen',
            assignee: CLARA, priority: 'LOWEST', dueDate: null, position: 0,
            laneEnteredAt: daysAgo(2), checklistTotal: 0, checklistChecked: 0, attachmentCount: 0,
        },
    ],
}

/** The discussion under the ticket, drawn by the application's own comment thread. */
export const TICKET_COMMENTS: Comment[] = [
    {
        id: 1, parentId: null, author: BEN, authorName: 'Ben Krüger',
        content: 'Zwei Heringe fehlen, der Rest ist heil. Mängelliste hängt dran.',
        createdAt: daysAgo(1),
    },
    {
        id: 2, parentId: 1, author: CLARA, authorName: 'Clara Weiß',
        content: '@[Ben Krüger] Danke - Nachschub steht schon in der Beschaffung.',
        createdAt: daysAgo(1),
    },
    {
        id: 3, parentId: null, author: ANNA, authorName: 'Anna Müller',
        content: 'Das große Zelt bitte vor der Fahrt noch einmal aufbauen.',
        createdAt: daysAgo(3),
    },
]
