/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
/**
 * A button of the editor's toolbar: an icon, lit while what it sets applies at the cursor, and named by
 * its label on hover and for a screen reader. Pressing it keeps the focus in the text, so the selection
 * it acts on stays. What follows the icon, such as the size or family at the cursor, goes in the slot.
 */
defineProps<{
  icon: string[]
  label: string
  active: boolean
  disabled?: boolean
}>()
</script>

<template>
  <button
    type="button"
    :title="label"
    :aria-label="label"
    :disabled="disabled"
    :class="['inline-flex items-center p-1.5 rounded text-sm transition-colors disabled:opacity-40 disabled:cursor-not-allowed',
             active ? 'text-primary bg-primary/10' : 'text-[var(--text)] hover:bg-[var(--bg-accent)]']"
    @mousedown.prevent
  >
    <font-awesome-icon :icon="icon" class="w-3.5 h-3.5" />
    <slot/>
  </button>
</template>
