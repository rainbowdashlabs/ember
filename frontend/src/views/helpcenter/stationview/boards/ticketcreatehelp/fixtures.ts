/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {LinkType, TicketPriority} from '@/api/boards'
import type {BoardLabel, BoardLane, MemberCompletion} from '@/api/generated/schema'
import type {DraftChecklistItem} from '@/views/stationview/boards/ticketcreateview/TicketChecklistDraft.vue'
import type {DraftLink, TicketOption} from '@/views/stationview/boards/ticketcreateview/TicketLinksDraft.vue'
import type {DraftWeblink} from '@/views/stationview/boards/ticketcreateview/TicketWeblinksDraft.vue'

type Translate = (key: string) => string

const BOARD_ID = 1

function member(id: number, name: string): MemberCompletion {
    return {id, stationUid: 'help-station', memberUid: `help-member-${id}`, name, nameColor: null, stationName: null, displayTag: null}
}

/** A half filled ticket form as the create page holds it, for the help article to show. */
export function ticketCreateFixtures(t: Translate) {
    const lanes: BoardLane[] = [
        {id: 1, boardId: BOARD_ID, name: t('helpCenter.sample.boards.backlog'), color: null, position: 0},
        {id: 2, boardId: BOARD_ID, name: t('helpCenter.ticketCreate.laneToDo'), color: '#6b7280', position: 1},
    ]

    const members: MemberCompletion[] = [
        member(1, t('helpCenter.sample.people.maxMustermann')),
        member(2, t('helpCenter.sample.people.lisaSchmidt')),
        member(3, t('helpCenter.sample.people.tomMueller')),
    ]

    const labels: BoardLabel[] = [
        {id: 1, boardId: BOARD_ID, name: t('helpCenter.sample.boards.urgent'), color: '#ec2929'},
        {id: 2, boardId: BOARD_ID, name: t('helpCenter.sample.boards.training'), color: '#3694FF'},
    ]

    const checklistItems: DraftChecklistItem[] = [
        {key: 1, title: t('helpCenter.ticketCreate.checkItem1'), checked: true},
        {key: 2, title: t('helpCenter.ticketCreate.checkItem2'), checked: false},
    ]

    const weblinks: DraftWeblink[] = [
        {key: 1, url: 'https://example.org/bestellliste', title: t('helpCenter.ticketCreate.weblinkExample')},
    ]

    const allTickets: TicketOption[] = [
        {id: 12, ticketNumber: 12, title: t('helpCenter.ticketCreate.linkedTicketExample')},
        {id: 10, ticketNumber: 10, title: t('helpCenter.sample.boards.orderHoses')},
        {id: 11, ticketNumber: 11, title: t('helpCenter.sample.boards.sortHelmets')},
    ]

    const ticketLinks: DraftLink[] = [{key: 1, linkedTicketId: 12, linkType: LinkType.BLOCKED_BY}]

    return {
        shortKey: 'JF',
        title: t('helpCenter.ticketCreate.exampleTicketTitle'),
        description: t('helpCenter.ticketCreate.exampleDescription'),
        checklistItems, weblinks, allTickets, ticketLinks, lanes, members, labels,
        selectedLabels: [labels[0]!],
        laneId: String(lanes[0]!.id),
        priority: TicketPriority.HIGH,
        assignee: String(members[0]!.id),
        dueDate: '2026-06-20',
    }
}
