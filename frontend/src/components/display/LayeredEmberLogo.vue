/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted } from 'vue'

export interface LogoLayer {
  name: string
  label: string
}

export type EyeDirection = 'left' | 'mid' | 'right'

const props = withDefaults(defineProps<{
  layers: LogoLayer[]
  activeLayers: Set<string>
  pixelSize?: number
  size?: string
  autoBlink?: boolean
  gazePositions?: EyeDirection[]
  bounce?: boolean
}>(), {
  pixelSize: 512,
  size: 'w-48 h-48',
  autoBlink: false,
  gazePositions: () => [],
  bounce: false,
})

const emit = defineEmits<{
  'update:activeLayers': [layers: Set<string>]
}>()

const allEyeLayers = new Set([
  'fire_eyes_left', 'fire_eyes_left_half', 'fire_blink_left',
  'fire_eyes_mid', 'fire_eyes_mid_half', 'fire_blink',
  'fire_eyes_right', 'fire_eyes_right_half', 'fire_blink_right',
  'fire_eyes_blink',
])

const layerToDirection: Record<string, EyeDirection> = {
  fire_eyes_left: 'left', fire_eyes_left_half: 'left', fire_blink_left: 'left',
  fire_eyes_mid: 'mid', fire_eyes_mid_half: 'mid', fire_blink: 'mid',
  fire_eyes_right: 'right', fire_eyes_right_half: 'right', fire_blink_right: 'right',
}

const directionVariants: Record<string, { open: string; half: string; blink: string }> = {
  left:  { open: 'fire_eyes_left',  half: 'fire_eyes_left_half',  blink: 'fire_blink_left' },
  mid:   { open: 'fire_eyes_mid',   half: 'fire_eyes_mid_half',   blink: 'fire_blink' },
  right: { open: 'fire_eyes_right', half: 'fire_eyes_right_half', blink: 'fire_blink_right' },
}

// All layers rendered in DOM always — visibility toggled via v-show
const displayedLayers = ref(new Set<string>())
let blinkTimeout: ReturnType<typeof setTimeout> | null = null
let gazeTimeout: ReturnType<typeof setTimeout> | null = null
let blinkRunning = false
let gazeRunning = false

function fragmentUrl(name: string): string {
  return `/api/v1/public/logo-fragment/${name}?size=${props.pixelSize}`
}

function isVisible(name: string): boolean {
  return displayedLayers.value.has(name)
}

function setEyeLayer(name: string) {
  for (const eye of allEyeLayers) displayedLayers.value.delete(eye)
  if (name) displayedLayers.value.add(name)
}

function getCurrentDirection(): EyeDirection | null {
  for (const name of displayedLayers.value) {
    if (layerToDirection[name]) return layerToDirection[name]
  }
  return null
}

function setActiveEye(openLayerName: string) {
  const next = new Set(props.activeLayers)
  for (const eye of allEyeLayers) next.delete(eye)
  next.add(openLayerName)
  emit('update:activeLayers', next)
}

function sleep(ms: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms))
}

// --- Sync from parent ---

watch(() => [...props.activeLayers], (active) => {
  displayedLayers.value = new Set(active)
}, { immediate: true })

// --- Preload on mount ---

onMounted(() => {
  // Preload all layers so animations never wait
  for (const layer of props.layers) {
    const img = new Image()
    img.src = fragmentUrl(layer.name)
  }
})

// --- Blink animation ---

async function doBlink() {
  const dir = getCurrentDirection()
  if (!dir || !directionVariants[dir]) return
  const v = directionVariants[dir]
  setEyeLayer(v.half)
  await sleep(60)
  if (!blinkRunning) return
  setEyeLayer(v.blink)
  await sleep(100)
  if (!blinkRunning) return
  setEyeLayer(v.half)
  await sleep(60)
  if (!blinkRunning) return
  setEyeLayer(v.open)
}

function scheduleNextBlink() {
  if (!blinkRunning) return
  const delay = 2000 + Math.random() * 3000
  blinkTimeout = setTimeout(async () => {
    if (!blinkRunning) return
    await doBlink()
    scheduleNextBlink()
  }, delay)
}

function startBlink() {
  if (blinkRunning) return
  blinkRunning = true
  scheduleNextBlink()
}

function stopBlink() {
  blinkRunning = false
  if (blinkTimeout) { clearTimeout(blinkTimeout); blinkTimeout = null }
  displayedLayers.value = new Set(props.activeLayers)
}

watch(() => props.autoBlink, (enabled) => {
  if (enabled) startBlink()
  else stopBlink()
})

// --- Gaze animation ---

async function doGazeShift(from: string, to: string) {
  const fromV = directionVariants[from]
  const toV = directionVariants[to]
  if (!fromV || !toV) return
  setEyeLayer(fromV.half)
  await sleep(80)
  if (!gazeRunning) return
  setEyeLayer(toV.half)
  await sleep(80)
  if (!gazeRunning) return
  setEyeLayer(toV.open)
  setActiveEye(toV.open)
}

function pickNextGazeDirection(): EyeDirection | null {
  const positions = props.gazePositions
  if (positions.length < 2) return null
  const current = getCurrentDirection()
  const others = positions.filter(d => d !== current)
  return others[Math.floor(Math.random() * others.length)]
}

function scheduleNextGaze() {
  if (!gazeRunning) return
  const delay = 3000 + Math.random() * 4000
  gazeTimeout = setTimeout(async () => {
    if (!gazeRunning) return
    const current = getCurrentDirection()
    const next = pickNextGazeDirection()
    if (!current || !next) { scheduleNextGaze(); return }
    await doGazeShift(current, next)
    scheduleNextGaze()
  }, delay)
}

function startGaze() {
  if (gazeRunning) return
  gazeRunning = true
  scheduleNextGaze()
}

function stopGaze() {
  gazeRunning = false
  if (gazeTimeout) { clearTimeout(gazeTimeout); gazeTimeout = null }
  displayedLayers.value = new Set(props.activeLayers)
}

watch(() => [...props.gazePositions], (positions) => {
  if (positions.length >= 2) {
    if (!gazeRunning) startGaze()
  } else {
    if (gazeRunning) stopGaze()
  }
}, { immediate: true })

onUnmounted(() => {
  stopBlink()
  stopGaze()
})
</script>

<template>
  <div class="relative" :class="[size, { 'layered-bounce': bounce }]">
    <img
      v-for="layer in layers"
      v-show="isVisible(layer.name)"
      :key="layer.name"
      :src="fragmentUrl(layer.name)"
      alt=""
      :class="['absolute inset-0 w-full h-full object-contain', { 'faq-shake': layer.name.includes('faq') }]"
    />
  </div>
</template>

<style scoped>
.layered-bounce {
  animation: layered-hop 3s ease-in-out infinite;
}

@keyframes layered-hop {
  0%, 80%, 100% { transform: translateY(0); }
  85% { transform: translateY(-4px); }
  90% { transform: translateY(0); }
  93% { transform: translateY(-2px); }
  96% { transform: translateY(0); }
}

.faq-shake {
  animation: faq-wobble 4s ease-in-out infinite;
  transform-origin: center center;
}

@keyframes faq-wobble {
  0%, 85%, 100% { transform: rotate(0deg); }
  88% { transform: rotate(-3deg); }
  91% { transform: rotate(3deg); }
  94% { transform: rotate(-2deg); }
  97% { transform: rotate(1deg); }
}
</style>
