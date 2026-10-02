/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {BoardLabel, BoardLane, MemberCompletion, MemberIdentity, TicketSummary} from '@/api/generated/schema'
import type {KanbanBoardSettings} from '@/components/kanban/kanbanLanes'

/** The demo board's short key, which every ticket number carries. */
export const demoBoard: KanbanBoardSettings = {shortKey: 'PLAN', backlogLaneId: null, hideDoneAfterDays: 14}

export const demoLanes: BoardLane[] = [
    {id: 1, boardId: 1, name: 'Offen', color: null, position: 0},
    {id: 2, boardId: 1, name: 'In Arbeit', color: '#3694FF', position: 1},
    {id: 3, boardId: 1, name: 'Erledigt', color: '#00C507', position: 2},
]

export const demoLabels: BoardLabel[] = [
    {id: 1, boardId: 1, name: 'Planung', color: '#3b82f6'},
    {id: 2, boardId: 1, name: 'Wartung', color: '#16a34a'},
    {id: 3, boardId: 1, name: 'Dringend', color: '#f97316'},
]

/**
 * Demo members without a station, so the avatars show initials and never ask the server for a
 * picture.
 */
export const demoMembers: MemberCompletion[] = [
    {id: 1, memberUid: 'demo-anna', name: 'Anna Schmidt', displayTag: null, nameColor: null, stationName: null, stationUid: ''},
    {id: 2, memberUid: 'demo-max', name: 'Max Müller', displayTag: null, nameColor: null, stationName: null, stationUid: ''},
]

function assignee(member: MemberCompletion): MemberIdentity {
    return {memberUid: member.memberUid, name: member.name, displayTag: null, nameColor: null, stationName: null, stationUid: ''}
}

function daysAgo(days: number): string {
    return new Date(Date.now() - days * 24 * 60 * 60 * 1000).toISOString()
}

function dateIn(days: number): string {
    return daysAgo(-days).slice(0, 10)
}

function ticket(fields: Partial<TicketSummary> & Pick<TicketSummary, 'id' | 'laneId' | 'title'>): TicketSummary {
    return {
        boardId: 1,
        ticketNumber: fields.id,
        position: fields.id,
        priority: 'MEDIUM',
        assignee: null,
        attachmentCount: 0,
        checklistChecked: 0,
        checklistTotal: 0,
        dueDate: null,
        laneEnteredAt: daysAgo(0),
        ...fields,
    }
}

export const demoTickets: TicketSummary[] = [
    ticket({id: 7, laneId: 1, title: 'Übungsplan für Juli erstellen', priority: 'HIGH', dueDate: dateIn(10), assignee: assignee(demoMembers[0]!)}),
    ticket({id: 8, laneId: 1, title: 'Funkgeräte prüfen'}),
    ticket({
        id: 5,
        laneId: 2,
        title: 'Atemschutzgeräte warten',
        priority: 'HIGHEST',
        dueDate: dateIn(-2),
        attachmentCount: 2,
        checklistChecked: 2,
        checklistTotal: 3,
        laneEnteredAt: daysAgo(3),
        assignee: assignee(demoMembers[1]!),
    }),
    ticket({id: 3, laneId: 3, title: 'Jahresplanung abschließen', priority: 'LOW', checklistChecked: 4, checklistTotal: 4, assignee: assignee(demoMembers[1]!)}),
    ticket({id: 1, laneId: 3, title: 'Schläuche reinigen', laneEnteredAt: daysAgo(30)}),
    ticket({id: 2, laneId: 3, title: 'Kassenprüfung vorbereiten', laneEnteredAt: daysAgo(30)}),
    ticket({id: 4, laneId: 3, title: 'Getränke für das Sommerfest bestellen', laneEnteredAt: daysAgo(30)}),
]

const demoTicketLabels: ReadonlyMap<number, number[]> = new Map([[7, [1]], [5, [2, 3]]])

/** The labels a demo ticket carries. */
export function demoLabelsForTicket(ticketId: number): BoardLabel[] {
    const ids = demoTicketLabels.get(ticketId) ?? []
    return demoLabels.filter(label => ids.includes(label.id))
}
