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
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PageHeader from '@/components/typography/PageHeader.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import ContainerNewModal from '@/views/stationview/inventory/storageview/ContainerNewModal.vue'
import UnknownScanModal from '@/views/stationview/inventory/UnknownScanModal.vue'
import Modal from '@/components/feedback/Modal.vue'
import IconButton from '@/components/button/IconButton.vue'
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
const editName = ref('')
const editInternalId = ref('')
const editDescription = ref('')
const editKindId = ref<number | null>(null)
const editParentId = ref<number | null>(null)
const showNewChildModal = ref(false)
const showDeleteConfirm = ref(false)
const submitting = ref(false)

const scanValue = ref('')
const scanError = ref('')
const scanSuccess = ref('')
const scanBusy = ref(false)

interface ScanEvent {
  id: number
  kind: 'item' | 'container'
  name: string
  internalId?: string | null
  ts: string
}

const scanHistory = ref<ScanEvent[]>([])
let scanCounter = 0
const unknownScanCode = ref<string | null>(null)

function flashScanError(msg: string) {
  scanError.value = msg
  setTimeout(() => (scanError.value = ''), 3500)
}

function flashScanSuccess(msg: string) {
  scanSuccess.value = msg
  setTimeout(() => (scanSuccess.value = ''), 2500)
}

async function handleScanAdd() {
  const term = scanValue.value.trim()
  if (!term || !detail.value) return
  scanValue.value = ''
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
        flashScanError(e?.response?.data?.message ?? t('inventory.storage.scan.containerFailed'))
        return
      }
      scanHistory.value.unshift({
        id: ++scanCounter,
        kind: 'container',
        name: container.name,
        internalId: container.internalId,
        ts: new Date().toISOString(),
      })
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
      scanHistory.value.unshift({
        id: ++scanCounter,
        kind: 'item',
        name: item.name ?? '',
        internalId: item.internalId,
        ts: new Date().toISOString(),
      })
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
    scanHistory.value.unshift({
      id: ++scanCounter,
      kind: 'item',
      name: item.name ?? '',
      internalId: item.internalId,
      ts: new Date().toISOString(),
    })
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
    resetEditForm()
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

function resetEditForm() {
  if (!detail.value) return
  const c = detail.value.container
  editName.value = c.name
  editInternalId.value = c.internalId ?? ''
  editDescription.value = c.description ?? ''
  editKindId.value = c.kindId ?? null
  editParentId.value = c.parentId ?? null
}

async function saveEdit() {
  if (!detail.value) return
  submitting.value = true
  error.value = ''
  try {
    await inventoryContainers.updateContainer(detail.value.container.id, {
      parentId: editParentId.value,
      internalId: editInternalId.value.trim() || null,
      name: editName.value.trim(),
      kindId: editKindId.value,
      description: editDescription.value,
    })
    editing.value = false
    await load()
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? t('inventory.storage.errors.updateFailed')
  } finally {
    submitting.value = false
  }
}

async function confirmDelete() {
  if (!detail.value) return
  submitting.value = true
  try {
    await inventoryContainers.deleteContainer(detail.value.container.id)
    router.push({name: 'inventory-storage'})
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? t('inventory.storage.errors.deleteFailed')
    submitting.value = false
    showDeleteConfirm.value = false
  }
}

function navigateToContainer(id: number) {
  router.push({name: 'inventory-container-detail', params: {id: String(id)}})
}

function navigateToItem(itemId: number) {
  router.push({name: 'inventory-detail', params: {id: String(itemId)}})
}

async function onChildCreated() {
  showNewChildModal.value = false
  await load()
}

watch(recursive, loadContents)
watch(containerId, load)

onMounted(load)
</script>

<template>
  <ViewContent>
    <div v-if="loading" class="flex justify-center py-12">
      <Spinner size="lg" />
    </div>
    <template v-else-if="detail">
      <div class="flex items-center flex-wrap gap-2 mb-3 text-sm text-(--text-muted)">
        <router-link :to="{name: 'inventory-storage'}" class="hover:underline">
          {{ t('inventory.storage.title') }}
        </router-link>
        <template v-for="(seg, i) in detail.pathSegments" :key="i">
          <span>/</span>
          <router-link
              v-if="i < detail.pathSegments.length - 1"
              :to="{name: 'inventory-container-detail', params: {id: String(detail.pathIds[i])}}"
              class="hover:underline"
          >
            {{ seg }}
          </router-link>
          <span v-else class="text-(--text)">{{ seg }}</span>
        </template>
      </div>

      <div class="flex items-center justify-between mb-4">
        <PageHeader>
          <font-awesome-icon
              :icon="['fas', kindById.get(detail.container.kindId ?? -1)?.icon ?? 'box']"
              class="mr-2 text-(--text-muted)"
          />
          {{ detail.container.name }}
        </PageHeader>
        <div class="flex gap-2">
          <EditButton @click="editing = !editing" :label="t('common.edit')" />
          <DeleteButton @click="showDeleteConfirm = true" :label="t('common.delete')" />
        </div>
      </div>

      <div v-if="error" class="mb-4">
        <Alert variant="error">{{ error }}</Alert>
      </div>

      <NeutralContainer v-if="editing" class="mb-4">
        <SubHeader class="mb-3">{{ t('inventory.storage.editTitle') }}</SubHeader>
        <div class="flex flex-col gap-3">
          <label class="flex flex-col gap-1 text-sm">
            <span>{{ t('inventory.storage.fields.name') }}</span>
            <TextInput v-model="editName" />
          </label>
          <label class="flex flex-col gap-1 text-sm">
            <span>{{ t('inventory.storage.fields.parent') }}</span>
            <SelectInput v-model="editParentId">
              <option :value="null">{{ t('inventory.storage.fields.parentNone') }}</option>
              <option
                  v-for="c in allContainers.filter(c => c.id !== detail!.container.id)"
                  :key="c.id"
                  :value="c.id"
              >{{ c.name }}</option>
            </SelectInput>
          </label>
          <label class="flex flex-col gap-1 text-sm">
            <span>{{ t('inventory.storage.fields.kind') }}</span>
            <SelectInput v-model="editKindId">
              <option :value="null">{{ t('inventory.storage.fields.kindNone') }}</option>
              <option v-for="k in kinds.filter(k => k.enabled)" :key="k.id" :value="k.id">{{ k.label }}</option>
            </SelectInput>
          </label>
          <label class="flex flex-col gap-1 text-sm">
            <span>{{ t('inventory.storage.fields.internalId') }}</span>
            <TextInput v-model="editInternalId" />
          </label>
          <label class="flex flex-col gap-1 text-sm">
            <span>{{ t('inventory.storage.fields.description') }}</span>
            <TextAreaInput v-model="editDescription" rows="3" />
          </label>
        </div>
        <div class="flex justify-end gap-2 mt-4">
          <SecondaryButton @click="editing = false; resetEditForm()">{{ t('common.cancel') }}</SecondaryButton>
          <PrimaryButton :disabled="submitting" @click="saveEdit">
            {{ submitting ? t('common.saving') : t('common.save') }}
          </PrimaryButton>
        </div>
      </NeutralContainer>

      <p v-if="detail.container.description" class="text-sm mb-4 whitespace-pre-line">
        {{ detail.container.description }}
      </p>

      <NeutralContainer class="mb-4">
        <SubHeader class="mb-2">{{ t('inventory.storage.scan.title') }}</SubHeader>
        <p class="text-xs text-(--text-muted) mb-2">{{ t('inventory.storage.scan.hint') }}</p>
        <div class="flex gap-2">
          <TextInput
              v-model="scanValue"
              :placeholder="t('inventory.storage.scan.placeholder')"
              @keydown.enter="handleScanAdd"
              class="flex-1"
              :disabled="scanBusy"
          />
          <PrimaryButton :disabled="scanBusy" @click="handleScanAdd">
            <font-awesome-icon :icon="['fas', 'barcode']" class="mr-2" />
            {{ t('inventory.storage.scan.action') }}
          </PrimaryButton>
        </div>
        <Alert v-if="scanError" variant="error" class="mt-2">{{ scanError }}</Alert>
        <Alert v-if="scanSuccess" variant="success" class="mt-2">{{ scanSuccess }}</Alert>
        <ul v-if="scanHistory.length > 0" class="mt-3 text-xs divide-y divide-(--bg-accent)">
          <li v-for="entry in scanHistory" :key="entry.id" class="py-1 flex items-center gap-2">
            <font-awesome-icon
                :icon="['fas', entry.kind === 'container' ? 'box-open' : 'cube']"
                class="text-(--text-muted)"
            />
            <span class="font-medium">{{ entry.name }}</span>
            <span v-if="entry.internalId" class="text-(--text-muted)">{{ entry.internalId }}</span>
            <span class="ml-auto text-(--text-muted)">{{ new Date(entry.ts).toLocaleTimeString() }}</span>
          </li>
        </ul>
      </NeutralContainer>

      <div class="flex items-center justify-between mb-2">
        <SectionHeader>{{ t('inventory.storage.contents') }}</SectionHeader>
        <div class="flex items-center gap-2 text-sm">
          <label class="flex items-center gap-1">
            <ToggleInput v-model="recursive" />
            <span>{{ t('inventory.storage.recursive') }}</span>
          </label>
          <PrimaryButton size="sm" @click="showNewChildModal = true">
            <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />
            {{ t('inventory.storage.addChild') }}
          </PrimaryButton>
        </div>
      </div>

      <NeutralContainer class="mb-6">
        <div v-if="contents && (contents.children.length || contents.items.length)" class="flex flex-col gap-4">
          <div v-if="contents.children.length > 0">
            <SubHeader class="mb-2">{{ t('inventory.storage.childContainers') }}</SubHeader>
            <ul class="divide-y divide-(--bg-accent)">
              <li
                  v-for="c in contents.children"
                  :key="c.id"
                  class="py-2 flex items-center gap-3 cursor-pointer hover:bg-(--bg-accent) rounded-theme px-2"
                  @click="navigateToContainer(c.id)"
              >
                <font-awesome-icon :icon="['fas', kindById.get(c.kindId ?? -1)?.icon ?? 'box']" class="w-4 text-(--text-muted)" />
                <span class="font-medium">{{ c.name }}</span>
                <span v-if="c.internalId" class="text-xs text-(--text-muted)">{{ c.internalId }}</span>
              </li>
            </ul>
          </div>
          <div v-if="contents.items.length > 0">
            <SubHeader class="mb-2">{{ t('inventory.storage.containedItems') }}</SubHeader>
            <ul class="divide-y divide-(--bg-accent)">
              <li
                  v-for="i in contents.items"
                  :key="i.id"
                  class="py-2 flex items-center gap-3 cursor-pointer hover:bg-(--bg-accent) rounded-theme px-2"
                  @click="navigateToItem(i.id)"
              >
                <font-awesome-icon :icon="['fas', 'cube']" class="w-4 text-(--text-muted)" />
                <span class="font-medium">{{ i.name ?? '' }}</span>
                <span v-if="i.internalId" class="text-xs text-(--text-muted)">{{ i.internalId }}</span>
              </li>
            </ul>
          </div>
        </div>
        <EmptyState v-else :message="t('inventory.storage.contentsEmpty')" />
      </NeutralContainer>

      <SectionHeader>{{ t('inventory.storage.history') }}</SectionHeader>
      <NeutralContainer>
        <ul v-if="history.length > 0" class="divide-y divide-(--bg-accent) text-sm">
          <li v-for="h in history" :key="h.id" class="py-2 flex items-center gap-3">
            <font-awesome-icon
                :icon="['fas', h.eventKind === 'MOVED' ? 'arrows-rotate' : h.eventKind === 'RENAMED' ? 'pen' : h.eventKind === 'DELETED' ? 'trash' : 'plus']"
                class="w-4 text-(--text-muted)"
            />
            <span class="font-medium">{{ t(`inventory.storage.events.${h.eventKind}`) }}</span>
            <span class="text-(--text-muted)">{{ new Date(h.eventTs).toLocaleString() }}</span>
          </li>
        </ul>
        <EmptyState v-else :message="t('inventory.storage.historyEmpty')" />
      </NeutralContainer>

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
