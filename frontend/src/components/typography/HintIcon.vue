/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
/**
 * An explanation kept out of the way until it is wanted, behind a small icon beside a label.
 *
 * <p>For the sentence that says how a setting behaves rather than what it is. Written out under the
 * control, such a sentence takes three lines of a narrow column and pushes whatever stands beside it
 * out of line; behind the icon it costs nothing until somebody reaches for it. It is a button rather
 * than a bare icon so that a keyboard reaches it and a screen reader reads it out.
 */
defineProps<{
  /** The explanation itself, read out as the icon's own label and shown when it is reached. */
  text: string
}>()
</script>

<template>
  <span class="group relative inline-flex align-middle">
    <button
        type="button"
        data-testid="hint-icon"
        :aria-label="text"
        :title="text"
        class="text-(--text-muted) transition-colors hover:text-(--text) focus-visible:text-(--text)"
    >
      <font-awesome-icon :icon="['fas', 'circle-info']" class="h-3.5 w-3.5"/>
    </button>
    <span
        role="tooltip"
        class="pointer-events-none absolute bottom-full left-1/2 z-30 mb-1 hidden w-56 -translate-x-1/2
               rounded-md border border-(--border) bg-(--bg) px-2 py-1 text-xs font-normal
               whitespace-normal text-(--text) shadow-lg group-hover:block group-focus-within:block"
    >
      {{ text }}
    </span>
  </span>
</template>
