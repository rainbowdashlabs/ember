/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { ref, watch, onMounted } from 'vue'

const props = defineProps<{
  bgClass: string
}>()

const el = ref<HTMLElement | null>(null)
const textColor = ref<string>('')

function updateTextColor() {
  if (!el.value) return
  const bg = getComputedStyle(el.value).backgroundColor
  const match = bg.match(/[\d.]+/g)
  if (!match || match.length < 3) return
  const [r, g, b] = match.map(Number)
  // Relative luminance (sRGB)
  const lin = (c: number) => { const s = c / 255; return s <= 0.04045 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4) }
  const lum = 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)
  textColor.value = lum > 0.4 ? '#1a1a1a' : '#ffffff'
}

onMounted(updateTextColor)
watch(() => props.bgClass, () => requestAnimationFrame(updateTextColor))
</script>

<template>
  <span
    ref="el"
    class="inline-flex items-center rounded-full px-2.5 py-0.5 text-[0.8rem] font-semibold"
    :class="bgClass"
    :style="textColor ? { color: textColor } : undefined"
  >
    <slot/>
  </span>
</template>
