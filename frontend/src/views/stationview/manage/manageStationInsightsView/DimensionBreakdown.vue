/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import MutedText from '@/components/typography/MutedText.vue'
import type {DimensionTotal} from '@/api/insights'

const props = defineProps<{
  rows: DimensionTotal[]
}>()

const {n} = useI18n()

const max = computed(() => props.rows.reduce((m, r) => Math.max(m, r.hits), 1))
</script>

<template>
  <div class="space-y-1">
    <div v-for="row in rows" :key="row.dimension" class="flex items-center gap-3 text-sm">
      <div class="w-32 truncate" :title="row.dimension">{{ row.dimension }}</div>
      <div class="flex-1 h-3 rounded bg-(--bg-accent) overflow-hidden">
        <div class="h-full bg-(--accent)" :style="{width: `${(row.hits / max) * 100}%`}"></div>
      </div>
      <div class="w-16 text-right font-mono">{{ n(row.hits) }}</div>
    </div>
    <MutedText v-if="rows.length === 0" tag="div" size="sm">—</MutedText>
  </div>
</template>
