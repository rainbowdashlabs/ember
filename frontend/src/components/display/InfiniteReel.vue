/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted, onUnmounted, ref} from 'vue'

const props = withDefaults(defineProps<{
  itemCount: number
  intervalMs?: number
}>(), {
  intervalMs: 5000,
})

const reel = ref<HTMLElement | null>(null)
const viewport = ref<HTMLElement | null>(null)
const offset = ref(0)
const hovered = ref(false)
const tileWidth = ref(0)
let timer = 0
let resizeObserver: ResizeObserver | null = null
let touchStartX = 0
let touchStartY = 0
let touchDeltaX = 0
let swiping = false

const GAP = 24

function updateTileWidth() {
  const el = viewport.value
  if (!el) return
  const w = el.clientWidth
  let cols = 3
  if (w < 640) cols = 1
  else if (w < 900) cols = 2
  tileWidth.value = (w - GAP * (cols - 1)) / cols
}

function getItemWidth(): number {
  return tileWidth.value + GAP
}

function scrollTo(index: number) {
  const el = reel.value
  if (!el) return
  el.style.transition = 'transform 0.5s ease'
  el.style.transform = `translateX(-${index * getItemWidth()}px)`
}

function advance() {
  if (hovered.value) return
  offset.value++
  scrollTo(offset.value)
}

function goNext() {
  offset.value++
  scrollTo(offset.value)
}

function goPrev() {
  if (offset.value <= 0) {
    const el = reel.value
    if (!el) return
    offset.value = props.itemCount
    el.style.transition = 'none'
    el.style.transform = `translateX(-${offset.value * getItemWidth()}px)`
    void el.offsetHeight
    offset.value--
    scrollTo(offset.value)
  } else {
    offset.value--
    scrollTo(offset.value)
  }
}

function handleTransitionEnd() {
  if (offset.value >= props.itemCount) {
    offset.value = 0
    const el = reel.value
    if (el) {
      el.style.transition = 'none'
      el.style.transform = 'translateX(0)'
    }
  }
}

function onTouchStart(e: TouchEvent) {
  touchStartX = e.touches[0].clientX
  touchStartY = e.touches[0].clientY
  touchDeltaX = 0
  swiping = false
}

function onTouchMove(e: TouchEvent) {
  const dx = e.touches[0].clientX - touchStartX
  const dy = e.touches[0].clientY - touchStartY
  if (!swiping && Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > 10) {
    swiping = true
  }
  if (swiping) {
    e.preventDefault()
    touchDeltaX = dx
    const el = reel.value
    if (el) {
      el.style.transition = 'none'
      el.style.transform = `translateX(${-(offset.value * getItemWidth()) + touchDeltaX}px)`
    }
  }
}

function onTouchEnd() {
  if (!swiping) return
  const threshold = getItemWidth() * 0.25
  if (touchDeltaX < -threshold) {
    goNext()
  } else if (touchDeltaX > threshold) {
    goPrev()
  } else {
    scrollTo(offset.value)
  }
  swiping = false
  touchDeltaX = 0
}

onMounted(() => {
  updateTileWidth()
  resizeObserver = new ResizeObserver(updateTileWidth)
  if (viewport.value) resizeObserver.observe(viewport.value)
  timer = setInterval(advance, props.intervalMs) as unknown as number
})

onUnmounted(() => {
  resizeObserver?.disconnect()
  clearInterval(timer)
})
</script>

<template>
  <div
    class="group flex items-center gap-2 sm:gap-3"
    @mouseenter="hovered = true"
    @mouseleave="hovered = false"
  >
    <!-- Left arrow -->
    <button
      class="shrink-0 flex items-center justify-center w-8 h-8 sm:w-10 sm:h-10 rounded-full border border-(--border) text-(--text) sm:opacity-0 sm:group-hover:opacity-100 transition-opacity duration-200 hover:bg-(--bg-accent) cursor-pointer"
      @click="goPrev"
    >
      <font-awesome-icon :icon="['fas', 'chevron-left']" class="h-3 w-3 sm:h-4 sm:w-4"/>
    </button>

    <!-- Reel -->
    <div
      ref="viewport"
      class="overflow-hidden flex-1 min-w-0 py-2"
      :style="tileWidth > 0 ? { '--tile-w': tileWidth + 'px' } : undefined"
      @touchstart="onTouchStart"
      @touchmove="onTouchMove"
      @touchend="onTouchEnd"
    >
      <div ref="reel" class="flex gap-6" @transitionend="handleTransitionEnd">
        <slot />
      </div>
    </div>

    <!-- Right arrow -->
    <button
      class="shrink-0 flex items-center justify-center w-8 h-8 sm:w-10 sm:h-10 rounded-full border border-(--border) text-(--text) sm:opacity-0 sm:group-hover:opacity-100 transition-opacity duration-200 hover:bg-(--bg-accent) cursor-pointer"
      @click="goNext"
    >
      <font-awesome-icon :icon="['fas', 'chevron-right']" class="h-3 w-3 sm:h-4 sm:w-4"/>
    </button>
  </div>
</template>
