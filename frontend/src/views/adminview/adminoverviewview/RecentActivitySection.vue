/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import SubHeader from '@/components/typography/SubHeader.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import {formatDateTime} from '@/util/format'
import type {RecentEntry} from './types'

defineProps<{
  title: string
  emptyText: string
  entries: RecentEntry[]
  truncateDetail?: boolean
}>()
</script>

<template>
  <div>
    <SubHeader>{{ title }}</SubHeader>
    <EmptyState v-if="entries.length === 0" compact class="mt-3">
      {{ emptyText }}
    </EmptyState>
    <ul v-else class="space-y-2 mt-3">
      <li v-for="entry in entries" :key="entry.id">
        <NeutralContainer>
          <div class="flex items-center justify-between gap-2 flex-wrap">
            <div :class="truncateDetail ? 'min-w-0' : ''">
              <p class="font-medium">{{ entry.title }}</p>
              <p v-if="entry.detail" :class="['text-xs text-(--text-muted)', truncateDetail ? 'truncate' : '']">
                {{ entry.detail }}
              </p>
            </div>
            <span class="text-xs text-(--text-muted)">{{ formatDateTime(entry.createdAt) }}</span>
          </div>
        </NeutralContainer>
      </li>
    </ul>
  </div>
</template>
