/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import type { RouteLocationRaw } from 'vue-router'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import RowLink from '@/components/navigation/RowLink.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import type { WaitingListWithCount } from '@/api/waitingList'
import { formatDate } from '@/util/format'

const props = withDefaults(defineProps<{
  lists: WaitingListWithCount[]
  /**
   * Whether a card opens its list. A preview of this screen shows the cards beside the rest of a
   * slide, where there is no list behind them to open.
   */
  linked?: boolean
}>(), {
  linked: true,
})

function listPage(id: number): RouteLocationRaw | null {
  return props.linked ? { name: 'waiting-list-detail', params: { id } } : null
}
</script>

<template>
  <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
    <RowLink v-for="item in lists" :key="item.list.id" :to="listPage(item.list.id)" class="h-full">
      <NeutralContainer class="h-full cursor-pointer hover:ring-2 hover:ring-primary/40 transition-all">
        <div class="space-y-3">
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-2">
              <SubHeader>{{ item.list.name }}</SubHeader>
              <SuccessBadge v-if="item.list.isPublic">
                {{ $t('waitingList.publicBadge') }}
              </SuccessBadge>
            </div>
            <PrimaryBadge>
              {{ item.entryCount }}
            </PrimaryBadge>
          </div>
          <p v-if="item.list.description" class="text-sm text-(--text-muted) line-clamp-2">
            {{ item.list.description }}
          </p>
          <p class="text-xs text-(--text-muted)">
            {{ $t('waitingList.createdAt') }}: {{ formatDate(item.list.createdAt) }}
          </p>
        </div>
      </NeutralContainer>
    </RowLink>
  </div>
</template>
