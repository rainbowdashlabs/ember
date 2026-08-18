/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import PitchBadge from './PitchBadge.vue'
import type {PitchScreen, PitchTab} from './pitchTypes'

/** Everything a screen shows between its filters and its content: switches, counts, progress. */
const props = defineProps<{
  screen: PitchScreen
}>()

const tabs = computed<PitchTab[]>(
    () => (props.screen.tabs ?? []).map(tab => typeof tab === 'string' ? {label: tab} : tab))

const activeTab = computed(() => Math.max(0, tabs.value.findIndex(tab => tab.selected)))
</script>

<template>
  <div v-if="screen.tabs || screen.actions" class="flex flex-wrap items-center gap-2">
    <SelectionToggleButton v-for="(tab, index) in tabs" :key="tab.label" :selected="index === activeTab" size="sm">
      <font-awesome-icon v-if="tab.done" :icon="['fas', 'circle-check']" class="mr-1 h-3 w-3 text-success"/>
      {{ tab.label }}
      <span v-if="tab.score" class="ml-1 font-mono">{{ tab.score }}</span>
    </SelectionToggleButton>
    <span class="flex-1"/>
    <PrimaryButton v-for="action in screen.actions ?? []" :key="action" compact>{{ action }}</PrimaryButton>
  </div>

  <div v-if="screen.summary" class="flex flex-wrap gap-x-4 gap-y-1 text-sm">
    <span v-for="item in screen.summary" :key="item.label" class="text-(--text-muted)">
      {{ item.label }}: <span class="font-medium text-(--text)">{{ item.value }}</span>
    </span>
  </div>

  <div v-if="screen.badges" class="flex flex-wrap gap-2">
    <PitchBadge v-for="badge in screen.badges" :key="badge.text" :tone="badge.tone">{{ badge.text }}</PitchBadge>
  </div>

  <SectionHeader v-if="screen.section" class="pt-1 text-sm! uppercase text-(--text-muted)">
    {{ screen.section }}
  </SectionHeader>

  <p v-if="screen.breadcrumb" class="text-xs text-(--text-muted)">
    <span v-for="(step, index) in screen.breadcrumb" :key="step">
      <span :class="index === screen.breadcrumb.length - 1 ? 'font-medium text-(--text)' : ''">{{ step }}</span>
      <span v-if="index < screen.breadcrumb.length - 1"> / </span>
    </span>
  </p>

  <MutedText v-if="screen.hint" tag="p" size="base">{{ screen.hint }}</MutedText>

  <div v-if="screen.total" class="flex items-center justify-between text-sm">
    <span class="text-(--text-muted)">{{ screen.total.label }}:</span>
    <span class="font-mono text-lg font-bold">{{ screen.total.value }}</span>
  </div>

  <div v-if="screen.progress" class="space-y-1">
    <div class="flex justify-between text-sm text-(--text-muted)">
      <span>{{ screen.progress.label }}</span>
      <span>{{ screen.progress.value }}</span>
    </div>
    <div class="h-2 w-full overflow-hidden rounded-full bg-bg-light-accent dark:bg-bg-dark-accent">
      <div class="h-full rounded-full bg-primary" :style="{width: `${screen.progress.percent}%`}"/>
    </div>
  </div>
</template>
