/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import InventoryTabs from './clusterinventoryview/InventoryTabs.vue'
import {clusterInventory} from '@/api'
import type {ClusterQueueEntry} from '@/api/clusterInventory'
import {useConfigPanel} from '@/composables/useConfigPanel'
import {formatDate} from '@/util/format'
import {useRouter} from 'vue-router'

const {t} = useI18n()
const router = useRouter()

const {config: queue, loading, error} = useConfigPanel<ClusterQueueEntry[]>({
  initial: [],
  fetch: () => clusterInventory.listQueue(),
})

/**
 * Opens the movement a step belongs to, which is where it is answered.
 *
 * <p>An exchange arriving from a station shows up here like any other step, because from this end it
 * is one: somebody sent something and is waiting on the association. There is no separate list of
 * exchanges to work, since the queue already is the work.
 */
function open(movementId: number) {
  void router.push({name: 'cluster-inventory-movement', params: {id: String(movementId)}})
}
</script>

<template>
  <ViewContent :subtitle="t('pages.cluster-movements.subtitle')" :title="t('pages.cluster-movements.title')">
    <div class="space-y-4">
      <InventoryTabs/>

      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <p class="text-sm text-(--text-muted)">{{ t('clusterMovements.hint') }}</p>

      <Spinner v-if="loading" size="lg"/>

      <template v-else>
        <EmptyState v-if="queue.length === 0">{{ t('clusterMovements.empty') }}</EmptyState>
        <div v-else class="space-y-2">
          <NeutralContainer
              v-for="entry in queue"
              :key="entry.movementId"
              class="flex flex-wrap items-center justify-between gap-3 cursor-pointer hover:border-primary transition-colors"
              @click="open(entry.movementId)"
          >
            <div class="min-w-0">
              <p class="font-medium truncate">{{ entry.stepLabel ?? t('clusterMovements.unnamedStep') }}</p>
              <p class="text-sm text-(--text-muted) truncate">
                {{ entry.stationName }}
                <template v-if="entry.itemName"> · {{ entry.itemName }}</template>
                · {{ formatDate(entry.createdAt) }}
              </p>
            </div>
            <SecondaryBadge>{{ t(`clusterMovements.purpose.${entry.purpose}`) }}</SecondaryBadge>
          </NeutralContainer>
        </div>
      </template>
    </div>
  </ViewContent>
</template>
