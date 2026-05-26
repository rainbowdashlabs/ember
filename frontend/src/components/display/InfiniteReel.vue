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
const offset = ref(0)
const hovered = ref(false)
let timer = 0

function getItemWidth(): number {
  const el = reel.value
  if (!el || !el.children[0]) return 0
  const child = el.children[0] as HTMLElement
  const gap = 24
  return child.offsetWidth + gap
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
    // Jump to end instantly, then animate back one
    const el = reel.value
    if (!el) return
    offset.value = props.itemCount
    el.style.transition = 'none'
    el.style.transform = `translateX(-${offset.value * getItemWidth()}px)`
    // Force reflow then animate
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

onMounted(() => {
  timer = setInterval(advance, props.intervalMs) as unknown as number
})

onUnmounted(() => {
  clearInterval(timer)
})
</script>

<template>
  <div
    class="group flex items-center gap-3"
    @mouseenter="hovered = true"
    @mouseleave="hovered = false"
  >
    <!-- Left arrow -->
    <button
      class="shrink-0 flex items-center justify-center w-10 h-10 rounded-full border border-(--border) text-(--text) opacity-0 group-hover:opacity-100 transition-opacity duration-200 hover:bg-(--bg-accent) cursor-pointer"
      @click="goPrev"
    >
      <font-awesome-icon :icon="['fas', 'chevron-left']" class="h-4 w-4"/>
    </button>

    <!-- Reel -->
    <div class="overflow-hidden flex-1 px-1 py-2">
      <div ref="reel" class="flex gap-6" @transitionend="handleTransitionEnd">
        <slot />
      </div>
    </div>

    <!-- Right arrow -->
    <button
      class="shrink-0 flex items-center justify-center w-10 h-10 rounded-full border border-(--border) text-(--text) opacity-0 group-hover:opacity-100 transition-opacity duration-200 hover:bg-(--bg-accent) cursor-pointer"
      @click="goNext"
    >
      <font-awesome-icon :icon="['fas', 'chevron-right']" class="h-4 w-4"/>
    </button>
  </div>
</template>
