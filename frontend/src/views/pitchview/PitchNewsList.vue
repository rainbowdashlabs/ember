/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import NewsListItem from '@/views/stationview/news/listview/NewsListItem.vue'
import type {PitchNews} from './pitchTypes'

/** The news of a station, drawn by the application's own list item. */
defineProps<{
  items: PitchNews[]
}>()

const noop = () => undefined
</script>

<template>
  <div class="space-y-4">
    <NewsListItem
        v-for="item in items" :key="item.id"
        v-bind="item" :can-edit-news="false" :comments-open="false"
        :set-view-badge-ref="noop" :on-request-delete="noop"/>
  </div>
</template>
