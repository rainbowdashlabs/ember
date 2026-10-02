/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {TicketPriority, type MemberCompletion, type MemberIdentity, type TicketSummary} from '@/api/generated/schema'

type Translate = (key: string) => string

const STATION_UID = 'help-station'
const BACKLOG_LANE_ID = 1

function member(id: number, name: string): MemberCompletion {
    return {id, stationUid: STATION_UID, memberUid: `help-member-${id}`, name, nameColor: null, stationName: null, displayTag: null}
}

function identityOf(completion: MemberCompletion): MemberIdentity {
    const {stationUid, memberUid, name, nameColor, stationName, displayTag} = completion
    return {stationUid, memberUid, name, nameColor, stationName, displayTag}
}

function ticket(ticketNumber: number, title: string, priority: TicketPriority, assignee: MemberCompletion | null, dueDate: string | null): TicketSummary {
    return {
        id: ticketNumber, ticketNumber, title, priority, dueDate, assignee: assignee ? identityOf(assignee) : null,
        boardId: 1, laneId: BACKLOG_LANE_ID, position: ticketNumber, attachmentCount: 0, checklistChecked: 0, checklistTotal: 0,
        laneEnteredAt: '2026-06-01T09:00:00Z',
    }
}

/** The backlog of a sample board, in the shapes the real backlog page receives it. */
export function backlogFixtures(t: Translate) {
    const max = member(1, t('helpCenter.backlog.dummyName1'))
    const lena = member(2, t('helpCenter.backlog.dummyName2'))
    return {
        shortKey: 'PLAN',
        members: [max, lena],
        tickets: [
            ticket(7, t('helpCenter.backlog.dummyTitle1'), TicketPriority.HIGHEST, max, '2026-07-15'),
            ticket(12, t('helpCenter.backlog.dummyTitle2'), TicketPriority.MEDIUM, lena, '2026-07-22'),
            ticket(15, t('helpCenter.backlog.dummyTitle3'), TicketPriority.LOW, null, null),
        ],
    }
}
