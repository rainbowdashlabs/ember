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
import ContainerNewModal from '@/views/stationview/inventory/storageview/ContainerNewModal.vue'
import ContainerEditPanel from '@/views/stationview/inventory/storageview/ContainerEditPanel.vue'
import AddExistingContainerModal from '@/views/stationview/inventory/storageview/AddExistingContainerModal.vue'
import {mapContainerError} from '@/views/stationview/inventory/storageview/containerErrors'
import AddChildChoiceModal from '@/views/stationview/inventory/storageview/AddChildChoiceModal.vue'
import ContainerHeader from '@/views/stationview/inventory/containerdetailview/ContainerHeader.vue'
import ContainerContentsSection from '@/views/stationview/inventory/containerdetailview/ContainerContentsSection.vue'
import ContainerHistorySection from '@/views/stationview/inventory/containerdetailview/ContainerHistorySection.vue'
import AddItemsModal from '@/views/stationview/inventory/containerdetailview/AddItemsModal.vue'
import Modal from '@/components/feedback/Modal.vue'
import {inventoryContainers} from '@/api'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {apiErrorMessage} from '@/util/apiError'
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
const showAddItemsModal = ref(false)
const showDeleteConfirm = ref(false)

function onAddChildChoice(target: 'existing' | 'new') {
  if (target === 'existing') showAddExistingModal.value = true
  else showNewChildModal.value = true
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
  } catch (e) {
    error.value = apiErrorMessage(e) ?? t('inventory.storage.loadError')
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

const {run: confirmDelete} = useAsyncAction(async () => {
  if (!detail.value) return
  try {
    await inventoryContainers.deleteContainer(detail.value.container.id)
    router.push({name: 'inventory-storage'})
  } catch (e) {
    error.value = mapContainerError(t, e, 'inventory.storage.errors.deleteFailed')
    showDeleteConfirm.value = false
  }
})

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

      <ContainerContentsSection
          :contents="contents"
          :root-container-id="detail.container.id"
          :kind-by-id="kindById"
          v-model:recursive="recursive"
          @add-items="showAddItemsModal = true"
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

      <AddItemsModal
          v-if="showAddItemsModal"
          :target-container-id="detail.container.id"
          :containers="allContainers"
          @added="loadContents"
          @close="showAddItemsModal = false"
      />
    </template>
    <Alert v-else variant="error">{{ error }}</Alert>
  </ViewContent>
</template>
