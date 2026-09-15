/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {onBeforeUnmount, ref, type Ref} from 'vue'
import {FieldWidths, type FieldWidthName} from '@/components/profilefields/fieldLayout'
import {moveWithin} from '@/util/reorder'
import type {AskedField} from './askedField'

/** The row is six columns wide, which is what each width costs. */
const SPANS: Record<FieldWidthName, number> = {
    [FieldWidths.FULL]: 6,
    [FieldWidths.HALF]: 3,
    [FieldWidths.THIRD]: 2,
}

/** The widths a field can be dragged to, narrowest first, so a span can be snapped to the nearest. */
const SNAP: ReadonlyArray<{width: FieldWidthName; span: number}> = [
    {width: FieldWidths.THIRD, span: 2},
    {width: FieldWidths.HALF, span: 3},
    {width: FieldWidths.FULL, span: 6},
]

/** The width whose span is closest to the one being dragged out. */
function nearestWidth(span: number): FieldWidthName {
    return SNAP.reduce((best, candidate) =>
        Math.abs(candidate.span - span) < Math.abs(best.span - span) ? candidate : best).width
}

/**
 * Arranging one audience's form by hand: dragging a question to a new place, and dragging its edge
 * to make it narrower or wider.
 *
 * <p>Pointer events rather than the browser's drag and drop, because the same code then works under
 * a finger. The order is mirrored here while a drag is under way so the grid reflows as it moves,
 * and the change is only handed on when the pointer is let go: a drag across six questions would
 * otherwise be six writes, five of which nobody meant.
 *
 * @param fields the form as it currently stands
 * @param onMove  called with the places to move between once a move is finished
 * @param onResize called with the field and its new width once a resize is finished
 */
export function usePreviewLayout(
    fields: Ref<AskedField[]>,
    onMove: (from: number, to: number) => void,
    onResize: (field: AskedField, width: FieldWidthName) => void,
) {
    /** The order being shown, which is the real one except while something is being dragged. */
    const order = ref<AskedField[] | null>(null)
    const movingId = ref<number | null>(null)
    const resizingId = ref<number | null>(null)
    /** The width the field under the pointer would take if the pointer were let go now. */
    const resizingTo = ref<FieldWidthName | null>(null)

    function shown(): AskedField[] {
        return order.value ?? fields.value
    }

    /** What the drag in progress has listening, so it can be taken off again exactly once. */
    let listening: (() => void) | null = null

    /**
     * Follows the pointer from the window rather than from the tile it started on.
     *
     * <p>The tile is what moves while a question is being dragged, and a captured pointer on an
     * element the framework then moves stops reporting: the release never arrives, the drag never
     * ends, and the next press appears to snap the question back. The window is always there.
     */
    function listen(move: (event: PointerEvent) => void, finish: () => void) {
        window.addEventListener('pointermove', move)
        window.addEventListener('pointerup', finish)
        window.addEventListener('pointercancel', finish)
        listening = () => {
            window.removeEventListener('pointermove', move)
            window.removeEventListener('pointerup', finish)
            window.removeEventListener('pointercancel', finish)
        }
    }

    function release() {
        listening?.()
        listening = null
    }

    onBeforeUnmount(release)

    /** Where the pointer is, as a place in the form, or null when it is not over one. */
    function indexUnder(event: PointerEvent): number | null {
        const element = document.elementFromPoint(event.clientX, event.clientY)
        const tile = element?.closest('[data-preview-index]')
        if (!tile) return null
        const index = Number((tile as HTMLElement).dataset.previewIndex)
        return Number.isNaN(index) ? null : index
    }

    function startMove(field: AskedField, event: PointerEvent) {
        if (resizingId.value !== null) return
        release()
        movingId.value = field.id
        order.value = [...fields.value]

        const move = (moved: PointerEvent) => {
            const from = shown().findIndex(entry => entry.id === field.id)
            const to = indexUnder(moved)
            if (to === null || to === from) return
            order.value = moveWithin(shown(), from, to)
        }

        const finish = () => {
            release()
            const from = fields.value.findIndex(entry => entry.id === field.id)
            const to = shown().findIndex(entry => entry.id === field.id)
            order.value = null
            movingId.value = null
            if (from !== to && from >= 0 && to >= 0) onMove(from, to)
        }

        listen(move, finish)
    }

    /**
     * Widens or narrows one question.
     *
     * <p>The grid is measured rather than assumed, so the snap points follow whatever the panel is
     * currently wide: the same drag lands on the same width in a narrow window and a wide one.
     */
    function startResize(field: AskedField, event: PointerEvent, grid: HTMLElement | null) {
        // Before anything can refuse the resize, or the tile behind the handle starts moving instead.
        event.stopPropagation()
        if (movingId.value !== null || !grid) return
        release()
        resizingId.value = field.id
        resizingTo.value = null
        const startX = event.clientX
        const column = grid.getBoundingClientRect().width / 6
        const startSpan = SPANS[(field.width ?? FieldWidths.FULL) as FieldWidthName] ?? 6

        const move = (moved: PointerEvent) => {
            if (column <= 0) return
            resizingTo.value = nearestWidth(startSpan + (moved.clientX - startX) / column)
        }

        const finish = () => {
            release()
            const width = resizingTo.value
            resizingId.value = null
            resizingTo.value = null
            if (width && width !== field.width) onResize(field, width)
        }

        listen(move, finish)
    }

    /** What one question is drawn at right now, which is the drag in progress where there is one. */
    function widthOf(field: AskedField): FieldWidthName {
        if (resizingId.value === field.id && resizingTo.value) return resizingTo.value
        return (field.width ?? FieldWidths.FULL) as FieldWidthName
    }

    return {shown, startMove, startResize, widthOf, movingId, resizingId}
}
