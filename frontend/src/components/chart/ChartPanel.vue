/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import type {EChartsCoreOption} from 'echarts/core'
import VChart from 'vue-echarts'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'

/**
 * One chart in its panel, with the heading, the height and the empty state decided in one place.
 *
 * Charts were previously wrapped by hand at every call site, which is how the panels drifted apart
 * in padding and in what they showed when there was nothing to plot. A panel with no data keeps its
 * frame and says so rather than collapsing, so a grid of charts does not reflow around the gap.
 */
withDefaults(defineProps<{
  option: EChartsCoreOption
  title?: string
  hasData?: boolean
  emptyText?: string
  height?: number
}>(), {
  title: undefined,
  hasData: true,
  emptyText: undefined,
  height: 280,
})
</script>

<template>
  <NeutralContainer class="space-y-2">
    <SectionHeader v-if="title">{{ title }}</SectionHeader>
    <slot/>
    <VChart v-if="hasData" :option="option" :style="{height: `${height}px`}" autoresize/>
    <MutedText v-else-if="emptyText" size="sm" tag="div">{{ emptyText }}</MutedText>
    <div v-else :style="{height: `${height}px`}"/>
  </NeutralContainer>
</template>
