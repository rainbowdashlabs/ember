/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'

const props = withDefaults(defineProps<{
  base?: string
  blinkBase?: string
  pixelSize?: number
  size?: string
  blink?: boolean
}>(), {
  base: 'NoBG_OrangeGlow_FAQ',
  blinkBase: 'NoBG_OrangeGlow_FAQ_Blink',
  pixelSize: 256,
  size: 'w-24 h-24',
  blink: false,
})

const isBlinking = ref(false)
let blinkInterval: ReturnType<typeof setInterval> | null = null

const baseSrc = computed(() => `/api/v1/public/logo/${props.base}?size=${props.pixelSize}`)
const blinkSrc = computed(() => `/api/v1/public/logo/${props.blinkBase}?size=${props.pixelSize}`)

onMounted(() => {
  if (props.blink) {
    blinkInterval = setInterval(() => {
      isBlinking.value = true
      setTimeout(() => { isBlinking.value = false }, 200)
    }, 3000 + Math.random() * 4000)
  }
})

onUnmounted(() => {
  if (blinkInterval) clearInterval(blinkInterval)
})
</script>

<template>
  <img :src="isBlinking ? blinkSrc : baseSrc" alt="Ember" :class="size" />
</template>
