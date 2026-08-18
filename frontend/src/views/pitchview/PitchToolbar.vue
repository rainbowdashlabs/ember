/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import type {PitchToolbar} from './pitchTypes'

/** The bar the lists carry above them: create on the left, view modes on the right. */
defineProps<{
  toolbar: PitchToolbar
}>()
</script>

<template>
  <div class="flex flex-wrap items-center justify-between gap-2">
    <PrimaryButton v-if="toolbar.create" :icon="['fas', 'plus']" compact>{{ toolbar.create }}</PrimaryButton>
    <span v-else/>
    <div class="flex gap-2">
      <SelectionToggleButton v-for="(view, index) in toolbar.views ?? []" :key="view"
                             :selected="index === 0" size="sm">{{ view }}</SelectionToggleButton>
    </div>
  </div>
</template>
