/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import type {PitchPanel} from './pitchTypes'

/**
 * The tile layout the dashboard uses: a container per panel, its heading carrying an icon and a
 * count, an action on the right, and the entries below as their own containers.
 */
defineProps<{
  panels: PitchPanel[]
}>()
</script>

<template>
  <div class="grid grid-cols-1 gap-3 lg:grid-cols-2">
    <NeutralContainer v-for="panel in panels" :key="panel.title" class="flex flex-col">
      <div class="mb-3 flex items-center justify-between gap-2">
        <SectionHeader class="text-sm!">
          <font-awesome-icon :icon="['fas', panel.icon]" class="mr-2"/>
          {{ panel.title }}<span v-if="panel.count"> ({{ panel.count }})</span>
        </SectionHeader>
        <SecondaryButton v-if="panel.action" compact>{{ panel.action }}</SecondaryButton>
      </div>
      <div class="space-y-2">
        <NeutralContainer v-for="entry in panel.entries" :key="entry.title" class="px-3 py-2">
          <p class="text-sm font-medium">{{ entry.title }}</p>
          <p class="text-xs text-(--text-muted)">{{ entry.meta }}</p>
        </NeutralContainer>
      </div>
    </NeutralContainer>
  </div>
</template>
