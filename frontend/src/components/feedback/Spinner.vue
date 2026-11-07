/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
/**
 * A turning wheel saying something is on its way.
 *
 * <p>The large size is what a page or a section shows while it loads, so it stands centred on a line
 * of its own with room above and below. Left inline it sat in the top left corner, where it reads as
 * a stray icon rather than as the page being busy. The small and medium sizes stay inline, beside the
 * label of a button or a row they belong to.
 */
defineProps<{
  size?: 'sm' | 'md' | 'lg'
}>()
</script>

<template>
  <div v-if="size === 'lg'" class="flex w-full justify-center py-12" role="status" data-testid="page-spinner">
    <font-awesome-icon class="text-3xl text-primary" :icon="['fas', 'spinner']" spin/>
  </div>
  <font-awesome-icon
      v-else
      :class="[size === 'sm' ? 'text-base' : 'text-xl', 'text-primary']"
      :icon="['fas', 'spinner']"
      spin
  />
</template>
