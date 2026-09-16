/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onBeforeUnmount, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import MutedText from '@/components/typography/MutedText.vue'
import type {Cover} from '@/composables/useScreenCapture'

/**
 * A picture with rectangles dragged over the parts that should not be seen.
 *
 * <p>Drag to cover, press a cover to take it off again. The rectangles are held in the picture's own
 * pixels and not the screen's, so the same covers mean the same thing whatever width this is drawn
 * at, and what is finally written out matches what was on the screen.
 *
 * <p>Pointer events rather than mouse events, because a phone is where somebody reports a problem
 * about a phone. Listeners go on the window rather than the element: a drag that leaves the picture
 * still ends where the finger lifts, and a drag that ends outside would otherwise never end at all.
 *
 * <p>The picture is shown as large as it fits and no larger, and the frame shrinks to it rather than
 * the other way round. A cover is placed as a share of the frame, so a frame wider than the picture
 * inside it would put every cover beside the thing it was drawn over.
 */
const props = defineProps<{
  picture: HTMLCanvasElement
  covers: Cover[]
}>()

const emit = defineEmits<{
  add: [cover: Cover]
  remove: [index: number]
}>()

const {t} = useI18n()

const frame = ref<HTMLDivElement>()
const preview = ref<HTMLImageElement>()
const drawing = ref<Cover | null>(null)
let release: (() => void) | null = null

const source = computed(() => props.picture.toDataURL('image/webp', 0.7))

/** What one picture pixel is worth on the screen right now, which is what a drag is read through. */
function scale(): number {
  const shown = preview.value?.clientWidth ?? props.picture.width
  return shown > 0 ? props.picture.width / shown : 1
}

function pointIn(event: PointerEvent): {x: number; y: number} | null {
  const box = preview.value?.getBoundingClientRect()
  if (!box) return null
  const factor = scale()
  return {
    x: Math.round((event.clientX - box.left) * factor),
    y: Math.round((event.clientY - box.top) * factor),
  }
}

function startDrag(event: PointerEvent) {
  const start = pointIn(event)
  if (!start) return
  event.preventDefault()
  drawing.value = {x: start.x, y: start.y, width: 0, height: 0}

  const move = (moved: PointerEvent) => {
    const at = pointIn(moved)
    if (!at) return
    drawing.value = {
      x: Math.min(start.x, at.x),
      y: Math.min(start.y, at.y),
      width: Math.abs(at.x - start.x),
      height: Math.abs(at.y - start.y),
    }
  }
  const finish = () => {
    if (drawing.value) emit('add', drawing.value)
    drawing.value = null
    stopListening()
  }
  window.addEventListener('pointermove', move)
  window.addEventListener('pointerup', finish)
  window.addEventListener('pointercancel', finish)
  release = () => {
    window.removeEventListener('pointermove', move)
    window.removeEventListener('pointerup', finish)
    window.removeEventListener('pointercancel', finish)
  }
}

function stopListening() {
  release?.()
  release = null
}

/** A cover as a share of the picture, so it sits where it belongs at any width this is drawn at. */
function styleOf(cover: Cover) {
  return {
    left: `${(cover.x / props.picture.width) * 100}%`,
    top: `${(cover.y / props.picture.height) * 100}%`,
    width: `${(cover.width / props.picture.width) * 100}%`,
    height: `${(cover.height / props.picture.height) * 100}%`,
  }
}

watch(() => props.picture, stopListening)
onBeforeUnmount(stopListening)
</script>

<template>
  <div class="space-y-1">
    <div
        ref="frame"
        class="relative mx-auto w-fit max-w-full overflow-hidden rounded-theme border border-bg-light-accent dark:border-bg-dark-accent touch-none select-none"
        data-testid="coverable-picture"
        @pointerdown="startDrag"
    >
      <img
          ref="preview"
          :src="source"
          alt=""
          class="block h-auto max-h-[70vh] w-auto max-w-full"
          draggable="false"
      />

      <button
          v-for="(cover, index) in props.covers"
          :key="`${cover.x}-${cover.y}-${index}`"
          :style="styleOf(cover)"
          :title="t('problemReport.coverRemove')"
          class="absolute bg-gray-900 hover:opacity-80"
          data-testid="picture-cover"
          type="button"
          @pointerdown.stop
          @click.stop="emit('remove', index)"
      />

      <div v-if="drawing" :style="styleOf(drawing)" class="absolute bg-gray-900/70 pointer-events-none"/>
    </div>
    <MutedText size="sm" tag="p">{{ t('problemReport.coverHint') }}</MutedText>
  </div>
</template>
