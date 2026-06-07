/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
const model = defineModel<string | number>()

const props = defineProps<{
  type?: 'text' | 'number' | 'date' | 'time' | 'datetime-local' | 'password' | 'email'
  placeholder?: string
  disabled?: boolean
  step?: string
  borderless?: boolean
}>()
</script>

<template>
  <input
      v-model="model"
      :disabled="disabled"
      :placeholder="placeholder"
      :step="step"
      :type="type ?? 'text'"
      :class="props.borderless
        ? 'w-full px-2 py-1 bg-transparent text-[var(--text)] transition-colors duration-150 outline-none rounded-theme hover:bg-[var(--bg-accent)] focus:bg-[var(--bg)] focus:ring-1 focus:ring-primary disabled:opacity-50 disabled:cursor-not-allowed'
        : 'w-full px-3 py-2 rounded-theme border border-bg-light-accent bg-bg-light text-[var(--text)] transition-colors duration-150 outline-none focus:border-primary focus:ring-1 focus:ring-primary disabled:opacity-50 disabled:cursor-not-allowed dark:border-bg-dark-accent dark:bg-bg-dark'"
  />
</template>
