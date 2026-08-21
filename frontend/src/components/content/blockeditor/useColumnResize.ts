/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { onBeforeUnmount, ref, type Ref } from 'vue'

const MIN_COLUMN_PERCENT = 10

/**
 * Dragging the divider between two page-editor columns.
 *
 * The listeners go on the document rather than the handle, because the pointer routinely leaves
 * a handle only a few pixels wide while dragging. For the same reason the whole document is
 * locked to the resize cursor and text selection is suppressed until the drag ends.
 *
 * A drag that would take either column below {@link MIN_COLUMN_PERCENT} is ignored rather than
 * clamped, so the columns cannot be dragged into a state the layout cannot render.
 *
 * @param leftPercent  the current width of the column on the left
 * @param rightPercent the current width of the column on the right
 * @param onResize     called with the width to move from the right column to the left one
 */
export function useColumnResize(
  leftPercent: Ref<number>,
  rightPercent: Ref<number>,
  onResize: (leftDelta: number) => void,
) {
  const dragging = ref(false)
  let startX = 0
  let containerWidth = 0

  function onMouseMove(event: MouseEvent) {
    if (!dragging.value) return
    const deltaPercent = ((event.clientX - startX) / containerWidth) * 100
    if (leftPercent.value + deltaPercent < MIN_COLUMN_PERCENT) return
    if (rightPercent.value - deltaPercent < MIN_COLUMN_PERCENT) return
    startX = event.clientX
    onResize(deltaPercent)
  }

  function onMouseUp() {
    dragging.value = false
    document.body.style.cursor = ''
    document.body.style.userSelect = ''
    document.removeEventListener('mousemove', onMouseMove)
    document.removeEventListener('mouseup', onMouseUp)
  }

  function onMouseDown(event: MouseEvent) {
    event.preventDefault()
    dragging.value = true
    startX = event.clientX
    const container = (event.currentTarget as HTMLElement | null)?.closest('.editor-row-cells')
    containerWidth = container?.getBoundingClientRect().width ?? 1
    document.body.style.cursor = 'col-resize'
    document.body.style.userSelect = 'none'
    document.addEventListener('mousemove', onMouseMove)
    document.addEventListener('mouseup', onMouseUp)
  }

  onBeforeUnmount(onMouseUp)

  return {dragging, onMouseDown}
}
