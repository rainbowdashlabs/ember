/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import {normaliseScannedPayload} from '@/components/scanner/useBarcodeScanner'
import ContainerNewModal from '@/views/stationview/inventory/storageview/ContainerNewModal.vue'
import ContainerEditPanel from '@/views/stationview/inventory/storageview/ContainerEditPanel.vue'
import AddExistingContainerModal from '@/views/stationview/inventory/storageview/AddExistingContainerModal.vue'
import {mapContainerError} from '@/views/stationview/inventory/storageview/containerErrors'
import AddChildChoiceModal from '@/views/stationview/inventory/storageview/AddChildChoiceModal.vue'
import UnknownScanModal from '@/views/stationview/inventory/UnknownScanModal.vue'
import ContainerHeader from '@/views/stationview/inventory/containerdetailview/ContainerHeader.vue'
import ContainerContentsSection from '@/views/stationview/inventory/containerdetailview/ContainerContentsSection.vue'
import ContainerHistorySection from '@/views/stationview/inventory/containerdetailview/ContainerHistorySection.vue'
import Modal from '@/components/feedback/Modal.vue'
import {inventory, inventoryContainers} from '@/api'
import type {InventoryItem} from '@/api/types'
import type {
  ContainerDetail,
  ContainerContents,
  InventoryContainer,
  InventoryContainerHistory,
  InventoryContainerKind,
} from '@/api/inventoryContainers'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()

const detail = ref<ContainerDetail | null>(null)
const contents = ref<ContainerContents | null>(null)
const allContainers = ref<InventoryContainer[]>([])
const kinds = ref<InventoryContainerKind[]>([])
const history = ref<InventoryContainerHistory[]>([])
const loading = ref(true)
const error = ref('')
const recursive = ref(false)
const editing = ref(false)
const showNewChildModal = ref(false)
const showAddExistingModal = ref(false)
const showAddChoiceModal = ref(false)
const showDeleteConfirm = ref(false)

function onAddChildChoice(target: 'existing' | 'new') {
  if (target === 'existing') showAddExistingModal.value = true
  else showNewChildModal.value = true
}
const submitting = ref(false)

const scanBusy = ref(false)
const scanError = ref('')
const scanSuccess = ref('')
const unknownScanCode = ref<string | null>(null)

function flashScanError(msg: string) {
  scanError.value = msg
  setTimeout(() => (scanError.value = ''), 3500)
}

function flashScanSuccess(msg: string) {
  scanSuccess.value = msg
  setTimeout(() => (scanSuccess.value = ''), 2500)
}

async function onCameraScan(value: string) {
  if (scanBusy.value || !detail.value) return
  const term = normaliseScannedPayload(value).trim()
  if (!term) return
  scanBusy.value = true
  try {
    const container = await inventoryContainers.resolveContainerByScan(term)
    if (container) {
      if (container.id === detail.value.container.id) {
        flashScanError(t('inventory.storage.scan.selfTarget'))
        return
      }
      if (container.parentId === detail.value.container.id) {
        flashScanSuccess(t('inventory.storage.scan.containerAlreadyHere', {name: container.name}))
        return
      }
      try {
        await inventoryContainers.updateContainer(container.id, {
          parentId: detail.value.container.id,
          internalId: container.internalId ?? null,
          name: container.name,
          kindId: container.kindId,
          description: container.description ?? '',
        })
      } catch (e: any) {
        flashScanError(mapContainerError(t, e, 'inventory.storage.scan.containerFailed'))
        return
      }
      flashScanSuccess(t('inventory.storage.scan.containerMoved', {name: container.name}))
      await Promise.all([loadContents(), loadHistory()])
      return
    }
    const item = await inventory.findByInternalId(term)
    if (item) {
      if (item.containerId === detail.value.container.id) {
        flashScanSuccess(t('inventory.storage.scan.itemAlreadyHere', {name: item.name ?? ''}))
        return
      }
      try {
        await inventoryContainers.setItemContainer(item.id, detail.value.container.id)
      } catch (e: any) {
        flashScanError(e?.response?.data?.message ?? t('inventory.storage.scan.itemFailed'))
        return
      }
      flashScanSuccess(t('inventory.storage.scan.itemPlaced', {name: item.name ?? ''}))
      await loadContents()
      return
    }
    unknownScanCode.value = term
  } finally {
    scanBusy.value = false
  }
}

async function onUnknownScanCreated(item: InventoryItem) {
  unknownScanCode.value = null
  if (!detail.value) return
  try {
    await inventoryContainers.setItemContainer(item.id, detail.value.container.id)
    flashScanSuccess(t('inventory.storage.scan.itemCreatedAndPlaced', {name: item.name ?? ''}))
    await loadContents()
  } catch (e: any) {
    flashScanError(e?.response?.data?.message ?? t('inventory.storage.scan.itemFailed'))
  }
}

const containerId = computed(() => Number(route.params.id))
const kindById = computed(() => {
  const map = new Map<number, InventoryContainerKind>()
  for (const k of kinds.value) map.set(k.id, k)
  return map
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    const id = containerId.value
    const [d, k, all] = await Promise.all([
      inventoryContainers.getContainer(id),
      inventoryContainers.listKinds(),
      inventoryContainers.listContainers(),
    ])
    detail.value = d
    kinds.value = k
    allContainers.value = all
    await Promise.all([loadContents(), loadHistory()])
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? t('inventory.storage.loadError')
  } finally {
    loading.value = false
  }
}

async function loadContents() {
  contents.value = await inventoryContainers.getContainerContents(containerId.value, recursive.value)
}

async function loadHistory() {
  history.value = await inventoryContainers.getContainerHistory(containerId.value)
}

async function onEditSaved() {
  editing.value = false
  await load()
}

function onEditError(message: string) {
  error.value = message
}

async function confirmDelete() {
  if (!detail.value) return
  submitting.value = true
  try {
    await inventoryContainers.deleteContainer(detail.value.container.id)
    router.push({name: 'inventory-storage'})
  } catch (e: any) {
    error.value = mapContainerError(t, e, 'inventory.storage.errors.deleteFailed')
    submitting.value = false
    showDeleteConfirm.value = false
  }
}

function navigateToContainer(id: number) {
  router.push({name: 'inventory-container-detail', params: {id: String(id)}})
}

function navigateToItem(itemId: number) {
  router.push({name: 'inventory-item-detail', params: {id: String(itemId)}})
}

async function onChildCreated() {
  showNewChildModal.value = false
  await load()
}

function onKindCreated(kind: InventoryContainerKind) {
  if (kinds.value.some(k => k.id === kind.id)) return
  kinds.value = [...kinds.value, kind]
}

watch(recursive, loadContents)
watch(containerId, load)

onMounted(load)
</script>

<template>
  <ViewContent
      :title="t('pages.inventory-container-detail.title')"
      :subtitle="t('pages.inventory-container-detail.subtitle')"
  >
    <div v-if="loading" class="flex justify-center py-12">
      <Spinner size="lg" />
    </div>
    <template v-else-if="detail">
      <ContainerHeader
          :detail="detail"
          :kind-by-id="kindById"
          :error="error"
          @edit="editing = !editing"
          @delete="showDeleteConfirm = true"
      />

      <ContainerEditPanel
          v-if="editing"
          :container="detail.container"
          :containers="allContainers"
          :kinds="kinds"
          @saved="onEditSaved"
          @cancel="editing = false"
          @error="onEditError"
      />

      <p v-if="detail.container.description" class="text-sm mb-4 whitespace-pre-line">
        {{ detail.container.description }}
      </p>

      <Alert v-if="scanError" variant="error" class="mb-3">{{ scanError }}</Alert>
      <Alert v-if="scanSuccess" variant="success" class="mb-3">{{ scanSuccess }}</Alert>

      <ContainerContentsSection
          :contents="contents"
          :root-container-id="detail.container.id"
          :kind-by-id="kindById"
          v-model:recursive="recursive"
          :scan-busy="scanBusy"
          @scan="onCameraScan"
          @add-child="showAddChoiceModal = true"
          @open-container="navigateToContainer"
          @open-item="navigateToItem"
      />

      <ContainerHistorySection :history="history" />

      <Modal v-model="showDeleteConfirm" size="sm">
        <SubHeader class="mb-2">{{ t('inventory.storage.deleteTitle') }}</SubHeader>
        <p class="mb-4 text-sm">{{ t('inventory.storage.deleteWarning', {name: detail.container.name}) }}</p>
        <div class="flex justify-end gap-2">
          <SecondaryButton @click="showDeleteConfirm = false">{{ t('common.cancel') }}</SecondaryButton>
          <DeleteButton @click="confirmDelete" :label="t('common.delete')" />
        </div>
      </Modal>

      <ContainerNewModal
          v-if="showNewChildModal"
          :kinds="kinds"
          :containers="allContainers"
          :default-parent-id="detail.container.id"
          @created="onChildCreated"
          @close="showNewChildModal = false"
          @kind-created="onKindCreated"
      />

      <AddChildChoiceModal
          v-model:open="showAddChoiceModal"
          @choose="onAddChildChoice"
      />

      <AddExistingContainerModal
          v-if="showAddExistingModal"
          :target-container-id="detail.container.id"
          :containers="allContainers"
          :kinds="kinds"
          @moved="load"
          @close="showAddExistingModal = false"
      />

      <UnknownScanModal
          v-if="unknownScanCode"
          :scanned-code="unknownScanCode"
          context="container"
          @created="onUnknownScanCreated"
          @close="unknownScanCode = null"
      />
    </template>
    <Alert v-else variant="error">{{ error }}</Alert>
  </ViewContent>
</template>
