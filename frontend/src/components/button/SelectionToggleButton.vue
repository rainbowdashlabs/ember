/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
defineProps<{
  selected: boolean
  size?: 'sm' | 'md'
  /** Whether the choice is not open to the reader, such as a group bound to other member types. */
  disabled?: boolean
}>()

defineEmits<{
  toggle: []
}>()
</script>

<template>
  <button
    type="button"
    :aria-pressed="selected"
    :disabled="disabled"
    :class="[
      'rounded-theme font-medium border transition-all',
      size === 'md' ? 'px-3 py-1.5 text-sm' : 'px-3 py-1.5 text-xs',
      disabled ? 'cursor-not-allowed opacity-50' : 'cursor-pointer',
      selected
        ? 'border-primary bg-primary/10 text-primary ring-1 ring-primary/30'
        : 'border-bg-light-accent dark:border-bg-dark-accent text-(--text-muted)',
      selected || disabled ? '' : 'hover:border-primary',
    ]"
    @click="$emit('toggle')"
  >
    <slot />
  </button>
</template>
