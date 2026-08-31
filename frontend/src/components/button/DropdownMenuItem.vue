/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
/**
 * One entry of a dropdown.
 *
 * <p>`destructive` is the entry that deletes something. It is coloured, and by convention it is
 * the last one in the menu: a full width row reads as harmless when it sits between two harmless
 * ones, which a red button in a toolbar never did.
 */
defineProps<{
  icon?: string | string[]
  iconClass?: string
  disabled?: boolean
  destructive?: boolean
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
    :class="destructive ? 'text-error' : ''"
    @click="$emit('click')"
  >
    <font-awesome-icon v-if="icon" :icon="icon" :class="['w-4', iconClass ?? (destructive ? 'text-error' : 'text-[var(--primary)]')]" />
    <slot />
  </button>
</template>
