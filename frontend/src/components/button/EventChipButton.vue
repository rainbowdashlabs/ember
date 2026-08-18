/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
/**
 * Compact event chip used inside calendar cells. Differs from the regular `SecondaryButton`
 * shape in three ways:
 *   - Left-aligned content (calendar rows scan top-down, not centred)
 *   - Hard-clipped overflow without ellipsis - on a 35-px mobile cell an ellipsis swallows
 *     three letters we'd rather keep
 *   - Tiny padding so multiple chips stack inside a single cell
 */
/**
 * When `customStyle` is provided (e.g. from an event category color) the chip uses that
 * background + text color instead of the default primary tint. The fallback path keeps the
 * existing look so chips on uncolored categories don't visually change.
 */
defineProps<{
  title?: string
  customStyle?: {backgroundColor: string; color: string}
}>()

defineEmits<{
  click: [event: MouseEvent]
}>()
</script>

<template>
  <button
      type="button"
      :title="title"
      class="text-left w-full overflow-hidden whitespace-nowrap text-[10px] sm:text-xs rounded px-0.5 sm:px-1 py-0.5 cursor-pointer transition-opacity"
      :class="customStyle ? 'hover:opacity-80' : 'bg-primary/15 hover:bg-primary/30 text-primary'"
      :style="customStyle"
      @click="$emit('click', $event)"
  >
    <slot/>
  </button>
</template>
