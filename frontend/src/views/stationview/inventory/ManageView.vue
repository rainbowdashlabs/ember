/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useInventoryRoutes} from '@/composables/useInventoryRoutes'
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import StockActions from './manageview/StockActions.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import {inventory} from '@/api'
import type {InventorySummary} from '@/api/inventory'
import {useConfirmDelete} from '@/composables/useConfirmDelete'
import {useConfigPanel} from '@/composables/useConfigPanel'
import ScannerPanel from './manageview/ScannerPanel.vue'
import InventorySummaryCard from './manageview/InventorySummaryCard.vue'
import CreateInventoryModal from './manageview/CreateInventoryModal.vue'
import MovementFlowPanel from './manageview/MovementFlowPanel.vue'
import LossSettingsPanel from './manageview/LossSettingsPanel.vue'

const routes = useInventoryRoutes()

/** The heading, when the station's own wording is not the right one. */
const props = defineProps<{
  title?: string
  subtitle?: string
}>()

const {t} = useI18n()
const router = useRouter()

const {config: summaries, loading, error, reload} = useConfigPanel<InventorySummary[]>({
  initial: [],
  fetch: () => inventory.listSummaries(),
})

const showCreateModal = ref(false)

const {
  show: showDeleteModal,
  target: deleteTarget,
  requestDelete,
  confirm: confirmDelete,
} = useConfirmDelete<InventorySummary>({
  onDelete: inv => inventory.deleteInventory(inv.id),
  onSuccess: () => reload(),
  error,
})

function viewDetail(inv: InventorySummary) {
  router.push({name: routes.detail, params: {id: inv.id}})
}

function editInventory(inv: InventorySummary) {
  router.push({name: routes.edit, params: {id: inv.id}})
}

function onCreated() {
  reload()
}

function onError() {
  error.value = t('common.error')
}
</script>

<template>
  <ViewContent
      :title="props.title ?? t('pages.inventory-manage.title')"
      :subtitle="props.subtitle ?? t('pages.inventory-manage.subtitle')"
  >
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading">
        <StockActions @create="showCreateModal = true"/>

        <ScannerPanel />

        <EmptyState v-if="summaries.length === 0">{{ t('inventory.manage.empty') }}</EmptyState>

        <div class="space-y-3">
          <InventorySummaryCard
            v-for="inv in summaries"
            :key="inv.id"
            :inv="inv"
            @open="viewDetail"
            @edit="editInventory"
            @remove="requestDelete"
          />
        </div>

        <template v-if="!routes.settings">
          <LossSettingsPanel />

          <MovementFlowPanel />
        </template>
      </template>

      <CreateInventoryModal
        v-model="showCreateModal"
        @created="onCreated"
        @error="onError"
      />

      <ConfirmDeleteModal
        v-model="showDeleteModal"
        :message="t('inventory.manage.deleteConfirm', {name: deleteTarget?.name})"
        @confirm="confirmDelete"
      />
    </div>
  </ViewContent>
</template>
