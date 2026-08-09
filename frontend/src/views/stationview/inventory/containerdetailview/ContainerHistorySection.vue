/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import type {InventoryContainerHistory} from '@/api/inventoryContainers'
import {formatDateTime} from '@/util/format'

const props = defineProps<{
  history: InventoryContainerHistory[]
}>()

const {t} = useI18n()

function eventIcon(kind: string): string {
  if (kind === 'MOVED') return 'arrows-rotate'
  if (kind === 'RENAMED') return 'pen'
  if (kind === 'DELETED') return 'trash'
  return 'plus'
}
</script>

<template>
  <div>
    <SubHeader>{{ t('inventory.storage.history') }}</SubHeader>
    <NeutralContainer>
      <ul v-if="props.history.length > 0" class="divide-y divide-(--bg-accent) text-sm">
        <li v-for="h in props.history" :key="h.id" class="py-2 flex items-center gap-3">
          <font-awesome-icon
              :icon="['fas', eventIcon(h.eventKind)]"
              class="w-4 text-(--text-muted)"
          />
          <span class="font-medium">{{ t(`inventory.storage.events.${h.eventKind}`) }}</span>
          <span class="text-(--text-muted)">{{ formatDateTime(h.eventTs) }}</span>
        </li>
      </ul>
      <EmptyState v-else :message="t('inventory.storage.historyEmpty')" />
    </NeutralContainer>
  </div>
</template>
