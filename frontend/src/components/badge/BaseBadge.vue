/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { ref } from 'vue'
import { useContrastingText } from '@/composables/useContrastingText'

/**
 * The pill every badge in the product is built from.
 *
 * <p>The background is a utility class the caller names, so only the browser knows what it came
 * out as. The letters take their colour from what it painted, and follow it through a theme
 * change.
 */
const props = defineProps<{
  bgClass: string
}>()

const el = ref<HTMLElement | null>(null)
const textColor = useContrastingText(el, () => props.bgClass)
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
