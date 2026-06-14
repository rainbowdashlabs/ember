/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { ref, watch, onMounted } from 'vue'
import { contrastingTextColorForRgbString } from '@/util/contrastColor'

const props = defineProps<{
  bgClass: string
}>()

const el = ref<HTMLElement | null>(null)
const textColor = ref<string>('')

function updateTextColor() {
  if (!el.value) return
  const bg = getComputedStyle(el.value).backgroundColor
  const next = contrastingTextColorForRgbString(bg)
  if (next) textColor.value = next
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
