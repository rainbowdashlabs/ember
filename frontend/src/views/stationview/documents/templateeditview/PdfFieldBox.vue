/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {PdfFieldKind} from '@/api/generated/schema'
import type {ScreenBox} from './pdfViewport'
import type {FieldTextLook} from './pdfFieldLook'

/**
 * One field over the page: dragged to move, dragged by its corner to resize, and moved with the arrow
 * keys where a finger or a mouse is too coarse (a point at a time, ten with shift).
 *
 * <p>It reports how far it went in CSS pixels and leaves turning that into points to whoever holds the
 * page's viewport. A drag holds the pointer, so it keeps going when it runs past the box.
 *
 * <p>A text field shows its text the way it prints: in its size on the page as drawn, its alignment,
 * its font and style, and on one line or wrapped. The text is black whatever the theme, since it stands
 * on the white page; what does not fit the box is cut off as it would be on paper.
 */
const props = defineProps<{
  box: ScreenBox
  kind: PdfFieldKind
  label: string
  selected: boolean
  /** How a text field's text looks on the page as drawn; absent for a check mark or a signature. */
  look?: FieldTextLook | null
}>()

const emit = defineEmits<{
  select: []
  move: [dx: number, dy: number]
  resize: [dw: number, dh: number]
}>()

const {t} = useI18n()

const STEPS: Readonly<Record<string, [number, number]>> = {
  ArrowLeft: [-1, 0],
  ArrowRight: [1, 0],
  ArrowUp: [0, -1],
  ArrowDown: [0, 1],
}

const style = computed(() => ({
  left: `${props.box.left}px`,
  top: `${props.box.top}px`,
  width: `${props.box.width}px`,
  height: `${props.box.height}px`,
}))

const tone = computed(() => props.kind === PdfFieldKind.SIGNATURE
    ? 'border-secondary-accent bg-secondary/10'
    : 'border-primary bg-primary/10')

const textStyle = computed(() => {
  const look = props.look
  if (!look) return {}
  return {
    fontSize: `${look.fontSizePx}px`,
    textAlign: look.align,
    fontWeight: look.bold ? '700' : '400',
    fontStyle: look.italic ? 'italic' : 'normal',
    ...(look.fontFamily ? {fontFamily: look.fontFamily} : {}),
  }
})

let drag: {x: number; y: number; resizing: boolean} | null = null

function start(event: PointerEvent, resizing: boolean) {
  emit('select')
  const held = event.currentTarget as Element
  held.setPointerCapture(event.pointerId)
  drag = {x: event.clientX, y: event.clientY, resizing}
  event.stopPropagation()
}

function follow(event: PointerEvent) {
  if (!drag) return
  const dx = event.clientX - drag.x
  const dy = event.clientY - drag.y
  if (dx === 0 && dy === 0) return
  drag.x = event.clientX
  drag.y = event.clientY
  if (drag.resizing) emit('resize', dx, dy)
  else emit('move', dx, dy)
}

function stop() {
  drag = null
}

function step(event: KeyboardEvent) {
  const direction = STEPS[event.key]
  if (!direction) return
  event.preventDefault()
  const by = event.shiftKey ? 10 : 1
  emit('move', direction[0] * by, direction[1] * by)
}
</script>

<template>
  <div class="absolute touch-none select-none border-2 rounded-sm cursor-move focus:outline-none" :class="[tone, selected ? 'ring-2 ring-primary' : '']"
       :style="style" role="button" tabindex="0" :aria-label="label" :aria-pressed="selected" data-testid="pdf-field-box"
       @pointerdown="start($event, false)" @pointermove="follow" @pointerup="stop" @pointercancel="stop"
       @keydown="step" @focus="emit('select')">
    <span class="absolute inset-0 overflow-hidden px-0.5 leading-tight text-black" data-testid="pdf-field-text"
          :class="look ? (look.wrap ? 'whitespace-pre-wrap break-words' : 'whitespace-pre') : 'truncate text-xs font-medium'"
          :style="textStyle">{{ label }}</span>
    <span class="absolute -bottom-1.5 -right-1.5 size-3 rounded-full bg-primary cursor-nwse-resize" :title="t('documentTemplates.resizeField')"
          aria-hidden="true" @pointerdown="start($event, true)" @pointermove="follow" @pointerup="stop" @pointercancel="stop"/>
  </div>
</template>
