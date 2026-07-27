/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
defineProps<{
  icon?: string | string[]
  iconClass?: string
  disabled?: boolean
}>()

defineEmits<{
  click: []
}>()
</script>

<template>
  <button
    type="button"
    :disabled="disabled"
    class="w-full text-left px-4 py-2 text-sm hover:bg-bg-light-accent dark:hover:bg-bg-dark-accent flex items-center gap-2 transition-colors cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed"
    @click="$emit('click')"
  >
    <font-awesome-icon v-if="icon" :icon="icon" :class="['w-4', iconClass ?? 'text-[var(--primary)]']" />
    <slot />
  </button>
</template>
