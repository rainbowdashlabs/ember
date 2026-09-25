/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import type {RouteLocationRaw} from 'vue-router'

/**
 * One appointment as a month grid draws it, said as the link it is.
 *
 * <p>Pressing a chip has always opened the appointment, so it is a link and not a button: a reader
 * planning a month opens three of them in tabs, copies the address of one into a message, and reads
 * the destination in the status bar before deciding. A click handler offered none of that.
 *
 * <p>The shape differs from an ordinary button in three ways, all of them for a cell a few
 * characters wide: content sits to the left, because a calendar is scanned top down rather than
 * read; the overflow is clipped hard and without an ellipsis, which on a narrow phone cell would
 * swallow three letters worth keeping; and the padding is tiny so several chips stack in one day.
 *
 * <p>Where a category has a colour of its own, `customStyle` carries it and the chip is painted with
 * it. Without one the chip keeps the primary tint it always had, so a category nobody coloured looks
 * exactly as it did.
 */
const props = defineProps<{
  /**
   * Where the chip leads, or nothing where the reader may not open the appointment. Such a chip is
   * drawn the way it always was rather than as a link to a refusal.
   */
  to?: RouteLocationRaw | null
  title?: string
  customStyle?: {backgroundColor: string; color: string}
}>()

const CHIP = 'block text-left w-full overflow-hidden whitespace-nowrap text-[10px] sm:text-xs'
    + ' rounded px-0.5 sm:px-1 py-0.5 transition-opacity'

const chipClass = computed(() => [
  CHIP,
  props.customStyle ? 'text-inherit hover:opacity-80' : 'bg-primary/15 hover:bg-primary/30 text-primary',
])
</script>

<template>
  <NuxtLink
      v-if="props.to"
      :to="props.to"
      :title="title"
      :class="[chipClass, 'row-link cursor-pointer no-underline hover:no-underline']"
      :style="customStyle"
  >
    <slot/>
  </NuxtLink>
  <span v-else :title="title" :class="chipClass" :style="customStyle">
    <slot/>
  </span>
</template>
