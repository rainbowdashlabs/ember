/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import Spinner from '@/components/feedback/Spinner.vue'
import PitchSlide from './pitchview/PitchSlide.vue'
import PitchControls from './pitchview/PitchControls.vue'
import {PITCH_TRACKS, trackSlides} from './pitchview/pitchDeck'
import {getDemoStatus} from '@/api/demo'

const props = defineProps<{
  /** One-based position from the address, so a slide can be linked to directly. */
  column?: number
  row?: number
}>()

/**
 * The deck exists only where the instance invites people to look around: a demo or a development
 * one. A production instance answers as if the address were never there, which is why this decides
 * before anything is drawn rather than hiding the content afterwards.
 */
let known: boolean | null = null
const available = ref<boolean | null>(known)
const stage = ref<HTMLElement | null>(null)

const column = ref(clampColumn(props.column ?? 1))
const row = ref(0)

function clampColumn(value: number): number {
  return Math.min(Math.max(value - 1, 0), PITCH_TRACKS.length - 1)
}

const track = computed(() => PITCH_TRACKS[column.value]!)
const slides = computed(() => trackSlides(track.value))
const slide = computed(() => slides.value[row.value] ?? track.value.overview)

function moveColumn(delta: number) {
  column.value = Math.min(Math.max(column.value + delta, 0), PITCH_TRACKS.length - 1)
  row.value = 0
}

function moveRow(delta: number) {
  row.value = Math.min(Math.max(row.value + delta, 0), slides.value.length - 1)
}

/** The controls fade out while nothing happens, so the slide stands alone during a talk. */
const IDLE_DELAY = 2500
const idle = ref(false)
let idleTimer: ReturnType<typeof setTimeout> | undefined

function wake() {
  idle.value = false
  if (idleTimer) clearTimeout(idleTimer)
  idleTimer = setTimeout(() => idle.value = true, IDLE_DELAY)
}

const FORWARD = new Set(['ArrowRight', 'PageDown', ' ', 'Enter'])
const BACKWARD = new Set(['ArrowLeft', 'PageUp', 'Backspace'])

function onKeydown(event: KeyboardEvent) {
  wake()
  if (FORWARD.has(event.key)) moveColumn(1)
  else if (BACKWARD.has(event.key)) moveColumn(-1)
  else if (event.key === 'ArrowDown') moveRow(1)
  else if (event.key === 'ArrowUp') moveRow(-1)
  else if (event.key === 'Home') { column.value = 0; row.value = 0 }
  else if (event.key === 'End') { column.value = PITCH_TRACKS.length - 1; row.value = 0 }
  else if (event.key.toLowerCase() === 'f') toggleFullscreen()
  else return
  event.preventDefault()
}

async function toggleFullscreen() {
  if (document.fullscreenElement) await document.exitFullscreen()
  else await stage.value?.requestFullscreen().catch(() => undefined)
}

/*
 * The address follows the position without going through the router: a navigation would tear the
 * page down and build it up again between two slides, which is visible as a flash.
 */
watch([column, row], ([nextColumn, nextRow]) => {
  history.replaceState(history.state, '', `/pitch/${nextColumn + 1}/${nextRow + 1}`)
})

watch(() => [props.column, props.row], ([nextColumn, nextRow]) => {
  column.value = clampColumn(nextColumn ?? 1)
  row.value = Math.min(Math.max((nextRow ?? 1) - 1, 0), slides.value.length - 1)
}, {immediate: true})

onMounted(async () => {
  if (available.value === null) {
    try {
      const status = await getDemoStatus()
      known = status.demo || status.dev
    } catch {
      known = false
    }
    available.value = known
  }
  if (available.value) {
    window.addEventListener('keydown', onKeydown)
    wake()
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  if (idleTimer) clearTimeout(idleTimer)
})
</script>

<template>
  <div v-if="available === null" class="flex min-h-dvh items-center justify-center bg-(--bg)">
    <Spinner size="lg"/>
  </div>

  <div v-else-if="!available"
       class="flex min-h-dvh flex-col items-center justify-center gap-2 bg-(--bg) px-6 text-center">
    <p class="text-3xl font-extrabold">404</p>
    <p class="text-(--text-muted)">Diese Seite gibt es auf dieser Instanz nicht.</p>
  </div>

  <div v-else ref="stage" class="relative h-dvh overflow-hidden bg-(--bg)" @mousemove="wake">
    <Transition name="slide-fade">
      <PitchSlide :key="slide.id" :slide="slide" class="absolute inset-0"
                  :position="`${column + 1} / ${PITCH_TRACKS.length}`"/>
    </Transition>
    <PitchControls
        :idle="idle"
        :column="column + 1" :columns="PITCH_TRACKS.length"
        :row="row + 1" :rows="slides.length"
        @left="moveColumn(-1)" @right="moveColumn(1)"
        @up="moveRow(-1)" @down="moveRow(1)"
        @fullscreen="toggleFullscreen"/>
  </div>
</template>

<style scoped>
/*
 * The slides overlap while they change: the leaving one stays until the entering one has drawn,
 * so a heavy screen never shows an empty frame in between.
 */
.slide-fade-enter-active {
  transition: opacity 220ms ease, transform 220ms ease;
}

.slide-fade-leave-active {
  transition: opacity 220ms ease;
}

.slide-fade-enter-from {
  opacity: 0;
  transform: translateY(0.75rem);
}

.slide-fade-leave-to {
  opacity: 0;
}
</style>
