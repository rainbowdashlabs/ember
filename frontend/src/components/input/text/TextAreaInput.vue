/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
const model = defineModel<string>()

defineProps<{
  placeholder?: string
  disabled?: boolean
  rows?: number
}>()
</script>

<template>
  <textarea
      v-model="model"
      :disabled="disabled"
      :placeholder="placeholder"
      :rows="rows ?? 4"
      class="w-full px-3 py-2 rounded-theme border border-bg-light-accent bg-bg-light text-[var(--text)] placeholder-[var(--text-muted)] transition-colors duration-150 outline-none focus:border-primary focus:ring-1 focus:ring-primary disabled:opacity-50 disabled:cursor-not-allowed dark:border-bg-dark-accent dark:bg-bg-dark resize-y"
  />
</template>
