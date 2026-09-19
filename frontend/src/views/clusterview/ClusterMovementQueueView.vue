/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import TableColumnPicker from '@/components/table/TableColumnPicker.vue'
import RecordTable from '@/components/table/RecordTable.vue'
import InventoryTabs from './clusterinventoryview/InventoryTabs.vue'
import {useClusterQueueTable} from './clustermovementqueueview/useClusterQueueTable'
import {clusterInventory} from '@/api'
import type {ClusterQueueEntry} from '@/api/clusterInventory'
import {useConfigPanel} from '@/composables/useConfigPanel'

const {t} = useI18n()
const router = useRouter()

const {config: queue, loading, error} = useConfigPanel<ClusterQueueEntry[]>({
  initial: [],
  fetch: () => clusterInventory.listQueue(),
})

const table = useClusterQueueTable(() => queue.value)

/**
 * Opens the movement a step belongs to, which is where it is answered.
 *
 * <p>An exchange arriving from a station shows up here like any other step, because from this end it
 * is one: somebody sent something and is waiting on the association. There is no separate list of
 * exchanges to work, since the queue already is the work.
 */
function open(entry: ClusterQueueEntry) {
  void router.push({name: 'cluster-inventory-movement', params: {id: String(entry.movementId)}})
}
</script>

<template>
  <ViewContent :subtitle="t('pages.cluster-movements.subtitle')" :title="t('pages.cluster-movements.title')">
    <div class="space-y-4">
      <InventoryTabs/>

      <FailureAlert :message="error"/>

      <div class="flex items-center justify-between gap-2">
        <p class="text-sm text-(--text-muted)">{{ t('clusterMovements.hint') }}</p>
        <TableColumnPicker :table="table"/>
      </div>

      <Spinner v-if="loading" size="lg"/>

      <RecordTable v-else :table="table" clickable row-test-id="cluster-movement-row" test-id="cluster-movement-queue" @row-click="open">
        <template #cell-purpose="{text}">
          <SecondaryBadge>{{ text }}</SecondaryBadge>
        </template>
        <template #empty>
          <EmptyState>{{ t('clusterMovements.empty') }}</EmptyState>
        </template>
      </RecordTable>
    </div>
  </ViewContent>
</template>
