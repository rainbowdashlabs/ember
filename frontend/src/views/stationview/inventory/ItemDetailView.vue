/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import NoteEditor from '@/components/comment/NoteEditor.vue'
import {inventory, inventoryContainers, stationMembers} from '@/api'
import type {InventoryItem, InventoryItemHistory, InventorySize, StationMember} from '@/api/types'
import type {ItemLocationResponse} from '@/api/inventoryContainers'
import {StationPermission} from '@/api/types'
import {useSession} from '@/composables/useSession'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useFlashMessage} from '@/composables/useFlashMessage'
import ItemMetadataPanel from './itemdetailview/ItemMetadataPanel.vue'
import ItemActionsPanel from './itemdetailview/ItemActionsPanel.vue'
import ItemHistoryPanel from './itemdetailview/ItemHistoryPanel.vue'
import ItemCheckHistoryPanel from './itemdetailview/ItemCheckHistoryPanel.vue'
import AssignItemModal from './itemdetailview/AssignItemModal.vue'
import type {ItemCheckHistoryEntry} from '@/api/inventoryContainers'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const {hasPermission} = useSession()
const canEdit = computed(() => hasPermission(StationPermission.INVENTORY_EDIT))

const itemId = computed(() => Number(route.params.id))
const item = ref<InventoryItem | null>(null)
const historyEntries = ref<InventoryItemHistory[]>([])
const checkHistory = ref<ItemCheckHistoryEntry[]>([])
const sizes = ref<InventorySize[]>([])
const members = ref<StationMember[]>([])
const location = ref<ItemLocationResponse | null>(null)
const {message: success, flash} = useFlashMessage(3000)

const isManager = computed(() => hasPermission('INVENTORY_MANAGER') || hasPermission('STATION_ADMINISTRATOR'))
const canEditItem = computed(() => canEdit.value || isManager.value)

const showAssignModal = ref(false)

const {loading, error} = useAsyncLoader(async () => {
  const [i, h, ch] = await Promise.all([
    inventory.getItem(itemId.value),
    inventory.getItemHistory(itemId.value),
    inventoryContainers.listItemCheckHistory(itemId.value).catch(() => []),
  ])
  item.value = i
  historyEntries.value = h
  checkHistory.value = ch
  const [s, m, loc] = await Promise.all([
    inventory.listSizes(i.inventoryId),
    stationMembers.listMembers(),
    inventoryContainers.getItemLocation(itemId.value).catch(() => null),
  ])
  sizes.value = s
  members.value = m
  location.value = loc
})

function onError() {
  error.value = t('common.error')
}

function onUpdated(updated: InventoryItem) {
  item.value = updated
  error.value = ''
}

async function doAssign(memberId: number) {
  error.value = ''
  try {
    const member = members.value.find(m => m.id === memberId)
    item.value = await inventory.assignItem(itemId.value, {
      memberId,
      memberName: member?.name ?? '',
    })
    showAssignModal.value = false
    historyEntries.value = await inventory.getItemHistory(itemId.value)
    flash(t('itemDetail.assigned'))
  } catch {
    onError()
  }
}

async function doUnassign() {
  error.value = ''
  try {
    item.value = await inventory.assignItem(itemId.value, {memberId: null})
    historyEntries.value = await inventory.getItemHistory(itemId.value)
    flash(t('itemDetail.unassigned'))
  } catch {
    onError()
  }
}

async function doMarkLost() {
  error.value = ''
  try {
    item.value = await inventory.markLost(itemId.value)
    flash(t('itemDetail.markedLost'))
  } catch {
    onError()
  }
}

async function doMarkFound() {
  error.value = ''
  try {
    item.value = await inventory.markFound(itemId.value)
    flash(t('itemDetail.markedFound'))
  } catch {
    onError()
  }
}
</script>

<template>
  <ViewContent
      :title="t('pages.inventory-item-detail.title')"
      :subtitle="t('pages.inventory-item-detail.subtitle')"
  >
    <div class="space-y-6">
      <div class="flex items-center gap-2">
        <SecondaryButton :icon="['fas', 'arrow-left']" @click="router.back()"/>
        <SectionHeader>{{ t('itemDetail.title') }}</SectionHeader>
      </div>

      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>
      <Alert v-if="success" variant="success">{{ success }}</Alert>

      <template v-if="!loading && item">
        <ItemMetadataPanel
          :item="item"
          :sizes="sizes"
          :members="members"
          :location="location"
          :can-edit-item="canEditItem"
          @updated="onUpdated"
          @error="onError"
        />

        <ItemActionsPanel
          v-if="canEditItem"
          :item="item"
          @assign="showAssignModal = true"
          @unassign="doUnassign"
          @mark-lost="doMarkLost"
          @mark-found="doMarkFound"
        />

        <ItemHistoryPanel :entries="historyEntries"/>

        <ItemCheckHistoryPanel :entries="checkHistory"/>

        <NeutralContainer v-if="isManager">
          <NoteEditor :entity-type="'ITEM'" :entity-id="itemId"/>
        </NeutralContainer>
      </template>

      <AssignItemModal
        v-model="showAssignModal"
        :members="members"
        @assign="doAssign"
      />
    </div>
  </ViewContent>
</template>
