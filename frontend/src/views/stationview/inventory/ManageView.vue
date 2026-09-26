/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useInventoryRoutes} from '@/composables/useInventoryRoutes'
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import StockActions from './manageview/StockActions.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import {inventory, lending} from '@/api'
import type {InventorySummary} from '@/api/inventory'
import type {ShareSetting} from '@/api/lending'
import {useConfirmDelete} from '@/composables/useConfirmDelete'
import {useConfigPanel} from '@/composables/useConfigPanel'
import {useLendingShare} from '@/composables/useLendingShare'
import {describeFailure} from '@/util/failure'
import ScannerPanel from './manageview/ScannerPanel.vue'
import InventorySummaryCard from './manageview/InventorySummaryCard.vue'
import CreateInventoryModal from './manageview/CreateInventoryModal.vue'
import LossSettingsPanel from './manageview/LossSettingsPanel.vue'
import TagsPanel from './manageview/TagsPanel.vue'

const routes = useInventoryRoutes()

/** The heading, when the station's own wording is not the right one. */
const props = defineProps<{
  title?: string
  subtitle?: string
}>()

const {t} = useI18n()
const router = useRouter()

const {config: summaries, loading, failure, reload} = useConfigPanel<InventorySummary[]>({
  initial: [],
  fetch: () => inventory.listSummaries(),
})

const showCreateModal = ref(false)

const {visible: sharing} = useLendingShare()
const shares = ref(new Map<number, ShareSetting>())

/**
 * What each inventory is currently offered as, asked once for the whole list rather than once per
 * card. Only the rows written on an inventory matter here: a row on a kind or on a piece narrows
 * that decision and is read on the screen that thing lives on.
 *
 * <p>Said out loud when it fails, because the cards then show no offer at all, which reads exactly
 * like an inventory nobody has shared.
 */
async function loadShares() {
  if (!sharing.value) return
  try {
    const details = await lending.listShares()
    shares.value = new Map(details
        .filter(detail => detail.share.inventoryId != null)
        .map(detail => [detail.share.inventoryId as number, lending.settingOf(detail)]))
  } catch (e) {
    failure.value = {...describeFailure(e, t), message: t('inventory.manage.sharesUnreadable')}
  }
}

watch(sharing, mayShare => {
  if (mayShare) void loadShares()
}, {immediate: true})

const {
  show: showDeleteModal,
  target: deleteTarget,
  requestDelete,
  confirm: confirmDelete,
} = useConfirmDelete<InventorySummary>({
  onDelete: inv => inventory.deleteInventory(inv.id),
  onSuccess: () => reloadAfterWrite(),
  failure,
})

/**
 * Fetches the list again after something was deleted or created.
 *
 * <p>The list's own loader keeps whatever went wrong fetching it, and left as it stands that sentence
 * reads as though the deletion had been refused. Renamed here to what it is: the write is through and
 * only the screen is behind.
 */
async function reloadAfterWrite() {
  await reload()
  if (failure.value) failure.value = {...failure.value, message: t('failure.staleAfterAction')}
}

function editInventory(inv: InventorySummary) {
  router.push({name: routes.edit, params: {id: inv.id}})
}

function onCreated() {
  void reloadAfterWrite()
}
</script>

<template>
  <ViewContent
      :title="props.title ?? t('pages.inventory-manage.title')"
      :subtitle="props.subtitle ?? t('pages.inventory-manage.subtitle')"
  >
    <div class="space-y-6">
      <slot name="before"/>

      <Spinner v-if="loading" size="lg" />
      <FailureAlert :failure="failure"/>

      <template v-if="!loading">
        <StockActions @create="showCreateModal = true"/>

        <ScannerPanel />

        <EmptyState v-if="summaries.length === 0">{{ t('inventory.manage.empty') }}</EmptyState>

        <div class="space-y-3">
          <InventorySummaryCard
            v-for="inv in summaries"
            :key="inv.id"
            :inv="inv"
            :share="shares.get(inv.id) ?? null"
            @edit="editInventory"
            @remove="requestDelete"
            @share-changed="loadShares"
          />
        </div>

        <template v-if="!routes.settings">
          <TagsPanel />
          <LossSettingsPanel />
        </template>
      </template>

      <CreateInventoryModal
        v-model="showCreateModal"
        @created="onCreated"
      />

      <ConfirmDeleteModal
        v-model="showDeleteModal"
        :message="t('inventory.manage.deleteConfirm', {name: deleteTarget?.name})"
        @confirm="confirmDelete"
      />
    </div>
  </ViewContent>
</template>
