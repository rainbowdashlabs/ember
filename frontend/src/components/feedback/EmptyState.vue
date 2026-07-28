/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
withDefaults(defineProps<{
  compact?: boolean
  /**
   * Convenience text shown when no default slot content is supplied. Lets callers bind a single
   * localized string instead of opening a slot for it.
   */
  message?: string
}>(), {
  compact: false,
  message: '',
})
</script>

<template>
  <div :class="compact ? 'py-4' : 'py-8'" class="text-center text-(--text-muted)">
    <slot>{{ message }}</slot>
  </div>
</template>
