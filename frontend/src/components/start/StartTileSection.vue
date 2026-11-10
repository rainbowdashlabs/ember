/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'

/**
 * A titled grid of start tiles: the ways of one kind to start something, such as the templates there are.
 * A note stands in for the tiles while there are none.
 */
defineProps<{
  /** What the ways in the grid have in common. */
  title: string
  /** What to say instead of the grid, given only while it is empty. */
  emptyNote?: string | null
}>()
</script>

<template>
  <div class="space-y-3">
    <SubHeader>{{ title }}</SubHeader>
    <MutedText v-if="emptyNote" tag="div" size="sm" class="py-2">{{ emptyNote }}</MutedText>
    <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
      <slot/>
    </div>
  </div>
</template>
