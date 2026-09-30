/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {afterEach, describe, expect, it} from 'vitest'
import {mount, type VueWrapper} from '@vue/test-utils'
import {nextTick} from 'vue'
import {VueDraggable} from 'vue-draggable-plus'
import KanbanBoard from './KanbanBoard.vue'
import type {BoardLane, BoardTicket} from '@/api/boards'

let wrapper: VueWrapper | null = null

afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    document.body.innerHTML = ''
})

function lane(id: number, name: string): BoardLane {
    return {id, boardId: 1, name, color: null, position: id}
}

function ticket(id: number, laneId: number, position: number): BoardTicket {
    return {
        id,
        boardId: 1,
        laneId,
        ticketNumber: id,
        title: `Ticket ${id}`,
        assignee: null,
        priority: 'MEDIUM',
        dueDate: null,
        position,
        laneEnteredAt: new Date().toISOString(),
        checklistTotal: 0,
        checklistChecked: 0,
        attachmentCount: 0,
    }
}

const lanes = [lane(1, 'Offen'), lane(2, 'In Arbeit'), lane(3, 'Erledigt')]
const tickets = [ticket(1, 1, 0), ticket(2, 1, 1), ticket(3, 2, 0)]

function mountBoard(props: Partial<InstanceType<typeof KanbanBoard>['$props']> = {}) {
    wrapper = mount(KanbanBoard, {
        attachTo: document.body,
        props: {
            board: {shortKey: 'OPS', backlogLaneId: null, hideDoneAfterDays: 7},
            lanes,
            tickets,
            labelsForTicket: () => [],
            ...props,
        },
        global: {stubs: {BaseBadge: true}},
    })
    return wrapper
}

async function openMenu(board: VueWrapper, ticketId: number) {
    await board.get(`[data-testid="ticket-move-${ticketId}-trigger"]`).trigger('click')
    await nextTick()
}

function menuEntry(label: string): HTMLButtonElement {
    const entries = [...document.body.querySelectorAll<HTMLButtonElement>('[role="menu"] button')]
    return entries.find(entry => entry.textContent?.trim() === label)!
}

function laneList(board: VueWrapper, laneId: number): HTMLElement {
    return board.get(`[data-lane-id="${laneId}"]`).element as HTMLElement
}

function card(board: VueWrapper, ticketId: number): HTMLElement {
    return board.get(`[data-ticket-id="${ticketId}"]`).element as HTMLElement
}

/**
 * A card is moved by dragging it or from its menu, and both have to end in the same write, or a board
 * that can only be used by keyboard or on a phone behaves differently from one used with a mouse.
 *
 * @vitest-environment happy-dom
 */
describe('KanbanBoard', () => {
    it('moves a card to the end of another lane from its menu', async () => {
        const board = mountBoard()

        await openMenu(board, 1)
        menuEntry('Nach „In Arbeit“').click()

        expect(board.emitted('move')).toEqual([[tickets[0], 2, 1]])
    })

    it('reports a drag to another lane as the same move', async () => {
        const board = mountBoard()

        board.findAllComponents(VueDraggable)[0]!.vm.$emit('end', {
            from: laneList(board, 1),
            to: laneList(board, 2),
            item: card(board, 1),
            oldIndex: 0,
            newIndex: 1,
        })

        expect(board.emitted('move')).toEqual([[tickets[0], 2, 1]])
    })

    it('moves a card down within its lane from its menu', async () => {
        const board = mountBoard()

        await openMenu(board, 1)
        menuEntry('Nach unten').click()

        expect(board.emitted('move')).toEqual([[tickets[0], 1, 1]])
    })

    it('offers no move up for the first card of a lane', async () => {
        const board = mountBoard()

        await openMenu(board, 1)

        expect(menuEntry('Nach oben').disabled).toBe(true)
        expect(menuEntry('Nach „Offen“')).toBeUndefined()
    })

    it('announces where the card went', async () => {
        const board = mountBoard()

        await openMenu(board, 3)
        menuEntry('Nach „Erledigt“').click()
        await nextTick()

        expect(board.get('[data-testid="kanban-announcement"]').text())
            .toBe('OPS-3 steht jetzt in „Erledigt“ an Stelle 1.')
    })

    it('places a card before the one it was put in front of when a filter hides some', async () => {
        const hidden = ticket(4, 2, 0)
        const shown = ticket(5, 2, 1)
        const board = mountBoard({tickets: [ticket(1, 1, 0), hidden, shown], filter: t => t.id !== 4})

        board.findAllComponents(VueDraggable)[0]!.vm.$emit('end', {
            from: laneList(board, 1),
            to: laneList(board, 2),
            item: card(board, 1),
            oldIndex: 0,
            newIndex: 0,
        })

        expect(board.emitted('move')![0]![2]).toBe(1)
    })

    it('offers neither the menu nor the drag on a read-only board', () => {
        const board = mountBoard({readOnly: true})

        expect(board.find('[data-testid^="ticket-move-"]').exists()).toBe(false)
        expect(board.findAllComponents(VueDraggable).every(list => list.props('disabled') === true)).toBe(true)
    })

    it('reaches the move menu from the keyboard', async () => {
        const board = mountBoard()
        const trigger = board.get('[data-testid="ticket-move-1-trigger"]')

        expect(trigger.element.tagName).toBe('BUTTON')
        expect(trigger.attributes('tabindex')).not.toBe('-1')
        expect(trigger.attributes('aria-label')).toBe('Ticket OPS-1 verschieben')

        await openMenu(board, 1)

        expect(document.activeElement?.textContent?.trim()).toBe('Nach unten')
    })
})
