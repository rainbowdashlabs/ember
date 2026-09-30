/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it, vi} from 'vitest'
import {ref} from 'vue'
import {useTicketMoves} from './useTicketMoves'
import type {BoardTicket} from '@/api/boards'

function ticket(id: number, laneId: number, position: number): BoardTicket {
    return {
        id,
        boardId: 1,
        laneId,
        ticketNumber: id * 10,
        title: `Ticket ${id}`,
        assignee: null,
        priority: 'MEDIUM',
        dueDate: null,
        position,
        laneEnteredAt: '2026-01-01T00:00:00Z',
        checklistTotal: 0,
        checklistChecked: 0,
        attachmentCount: 0,
    }
}

function setup() {
    const tickets = ref([ticket(1, 1, 0), ticket(2, 1, 1), ticket(3, 2, 0)])
    const targets = {move: vi.fn().mockResolvedValue(undefined), reorder: vi.fn().mockResolvedValue(undefined)}
    const reload = vi.fn().mockResolvedValue(undefined)
    return {tickets, targets, reload, ...useTicketMoves(tickets, targets, reload)}
}

/**
 * A move writes to the board the way the drag always did: a new lane is a move, a new place a reorder.
 *
 * @vitest-environment happy-dom
 */
describe('useTicketMoves', () => {
    it('moves a ticket into another lane at its place', async () => {
        const {tickets, targets, moveTicket} = setup()

        await moveTicket(tickets.value[0]!, 2, 1)

        expect(targets.move).toHaveBeenCalledWith(10, {toLaneId: 2, position: 1})
        expect(targets.reorder).not.toHaveBeenCalled()
        expect(tickets.value.find(t => t.id === 1)).toMatchObject({laneId: 2, position: 1})
    })

    it('reorders a lane with its whole new order', async () => {
        const {tickets, targets, moveTicket} = setup()

        await moveTicket(tickets.value[0]!, 1, 1)

        expect(targets.reorder).toHaveBeenCalledWith(10, {laneId: 1, orderedIds: [2, 1]})
        expect(targets.move).not.toHaveBeenCalled()
    })

    it('reloads the board when the server refuses', async () => {
        const {tickets, targets, reload, moveTicket} = setup()
        targets.move.mockRejectedValue(new Error('refused'))

        await moveTicket(tickets.value[0]!, 2, 0)

        expect(reload).toHaveBeenCalled()
    })
})
