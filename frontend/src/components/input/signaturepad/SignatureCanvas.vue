/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'

/**
 * A white sheet to sign on with a finger, a pen or the mouse.
 *
 * <p>Pointer events cover all three, and the sheet takes no touch scrolling, so a finger draws instead of
 * moving the page. Each stroke that ends hands the picture so far to the parent as a PNG; the sheet itself
 * stays transparent behind the ink, its white only the background it is shown on. A browser that gives the
 * sheet no drawing surface leaves it empty, and the typed signature is the way around it.
 */
defineProps<{
  /** What the sheet is, read out to a screen reader. */
  label: string
}>()

const emit = defineEmits<{change: [dataUrl: string | null]}>()

const WIDTH = 600
const HEIGHT = 200
const STROKE = 3

const sheet = ref<HTMLCanvasElement | null>(null)
const drawing = ref(false)
const inked = ref(false)

function context(): CanvasRenderingContext2D | null {
  return sheet.value?.getContext('2d') ?? null
}

/** Where a pointer is on the sheet, in the sheet's own pixels whatever size it is shown at. */
function pointOf(event: PointerEvent): [number, number] {
  const box = sheet.value?.getBoundingClientRect()
  if (!box || box.width === 0 || box.height === 0) return [event.offsetX, event.offsetY]
  return [(event.clientX - box.left) * WIDTH / box.width, (event.clientY - box.top) * HEIGHT / box.height]
}

function begin(event: PointerEvent) {
  const pen = context()
  if (!pen) return
  sheet.value?.setPointerCapture?.(event.pointerId)
  pen.lineWidth = STROKE
  pen.lineCap = 'round'
  pen.lineJoin = 'round'
  pen.strokeStyle = '#111111'
  pen.beginPath()
  pen.moveTo(...pointOf(event))
  drawing.value = true
}

function extend(event: PointerEvent) {
  const pen = context()
  if (!drawing.value || !pen) return
  pen.lineTo(...pointOf(event))
  pen.stroke()
  inked.value = true
}

function finish() {
  if (!drawing.value) return
  drawing.value = false
  emit('change', inked.value ? sheet.value?.toDataURL('image/png') ?? null : null)
}

/** Wipes the sheet for a fresh try. */
function clear() {
  context()?.clearRect(0, 0, WIDTH, HEIGHT)
  inked.value = false
  emit('change', null)
}

defineExpose({clear})
</script>

<template>
  <canvas
      ref="sheet"
      :width="WIDTH"
      :height="HEIGHT"
      role="img"
      :aria-label="label"
      class="block w-full max-w-xl aspect-[3/1] touch-none rounded border border-bg-light-accent dark:border-bg-dark-accent bg-white cursor-crosshair"
      data-testid="signature-canvas"
      @pointerdown="begin"
      @pointermove="extend"
      @pointerup="finish"
      @pointerleave="finish"
      @pointercancel="finish"
  />
</template>
