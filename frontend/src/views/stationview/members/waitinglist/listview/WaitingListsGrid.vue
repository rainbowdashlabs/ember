/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import type { WaitingListWithCount } from '@/api/types'

defineProps<{
  lists: WaitingListWithCount[]
}>()

const emit = defineEmits<{
  select: [id: number]
}>()

function formatDate(dateStr: string): string {
  return new Date(dateStr).toLocaleDateString()
}
</script>

<template>
  <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
    <NeutralContainer
      v-for="item in lists"
      :key="item.list.id"
      class="cursor-pointer hover:ring-2 hover:ring-primary/40 transition-all"
      @click="emit('select', item.list.id)"
    >
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
  </div>
</template>
