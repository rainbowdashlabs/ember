/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import SearchInput from '@/components/input/text/SearchInput.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import ScanButton from '@/components/scanner/ScanButton.vue'
import SizeBadge from '@/components/badge/SizeBadge.vue'
import {normaliseScannedPayload} from '@/components/scanner/useBarcodeScanner'
import {containerPathFor} from '@/util/containerPath'
import {inventory, inventoryContainers} from '@/api'
import {useAsyncAction} from '@/composables/useAsyncAction'
import type {InventoryItem, InventorySize} from '@/api/inventory'
import type {InventoryContainer} from '@/api/inventoryContainers'

const props = defineProps<{
  targetContainerId: number
  containers: InventoryContainer[]
}>()

const emit = defineEmits<{
  added: []
  close: []
}>()

const {t} = useI18n()

const open = ref(true)
const loading = ref(true)
const error = ref('')
const search = ref('')
const onlyUnstored = ref(false)
const items = ref<InventoryItem[]>([])
const sizes = ref<InventorySize[]>([])
const selectedIds = ref<Set<number>>(new Set())

const containerById = computed(() => {
  const map = new Map<number, InventoryContainer>()
  for (const c of props.containers) map.set(c.id, c)
  return map
})

const sizeById = computed(() => {
  const map = new Map<number, InventorySize>()
  for (const s of sizes.value) map.set(s.id, s)
  return map
})

function itemName(item: InventoryItem): string {
  return (item.name ?? '').trim() || item.internalId || `#${item.id}`
}

function sizeLabelFor(item: InventoryItem): string {
  return item.sizeId != null ? (sizeById.value.get(item.sizeId)?.label ?? '') : ''
}

const filtered = computed(() => {
  const term = search.value.trim().toLowerCase()
  return items.value.filter(i =>
      i.containerId !== props.targetContainerId
      && (!onlyUnstored.value || i.containerId == null)
      && (!term
          || (i.name ?? '').toLowerCase().includes(term)
          || (i.internalId ?? '').toLowerCase().includes(term)))
})

function locationLabel(item: InventoryItem): string {
  const path = containerPathFor(containerById.value, item.containerId)
  return path || t('inventory.storage.itemPathUnassigned')
}

function toggle(item: InventoryItem) {
  if (selectedIds.value.has(item.id)) selectedIds.value.delete(item.id)
  else selectedIds.value.add(item.id)
}

async function loadItems() {
  loading.value = true
  try {
    const [allItems, allSizes] = await Promise.all([inventory.listAllItems(), inventory.listAllSizes()])
    items.value = allItems
    sizes.value = allSizes
  } catch {
    error.value = t('inventory.storage.loadError')
  } finally {
    loading.value = false
  }
}

function onScan(value: string) {
  const term = normaliseScannedPayload(value).trim()
  if (!term) return
  error.value = ''
  const match = items.value.find(i => (i.internalId ?? '').toLowerCase() === term.toLowerCase())
  if (!match) {
    error.value = t('inventory.storage.addItems.scanNotFound', {scan: term})
    return
  }
  if (match.containerId === props.targetContainerId) {
    error.value = t('inventory.storage.scan.itemAlreadyHere', {name: match.name ?? ''})
    return
  }
  selectedIds.value.add(match.id)
}

const {running: submitting, run: submit} = useAsyncAction(async () => {
  if (selectedIds.value.size === 0) return
  error.value = ''
  let added = false
  try {
    for (const id of [...selectedIds.value]) {
      await inventoryContainers.setItemContainer(id, props.targetContainerId)
      selectedIds.value.delete(id)
      added = true
    }
    onClose()
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? t('inventory.storage.addItems.addFailed')
  } finally {
    if (added) emit('added')
  }
})

function onClose() {
  open.value = false
  emit('close')
}

onMounted(loadItems)
</script>

<template>
  <Modal v-model="open" size="lg" mobile-full @update:modelValue="(v) => { if (!v) onClose() }">
    <SubHeader class="mb-2">{{ t('inventory.storage.addItems.title') }}</SubHeader>
    <p class="text-xs text-(--text-muted) mb-3">{{ t('inventory.storage.addItems.intro') }}</p>

    <Alert v-if="error" variant="error" class="mb-3">{{ error }}</Alert>

    <div class="flex items-center gap-2 mb-2">
      <SearchInput v-model="search" :placeholder="t('inventory.storage.addItems.searchPlaceholder')" class="flex-1" autofocus />
      <ScanButton mode="continuous" :disabled="submitting" @decoded="onScan" />
    </div>

    <label class="flex items-center gap-2 mb-3 text-sm">
      <ToggleInput v-model="onlyUnstored" />
      <span>{{ t('inventory.storage.addItems.onlyUnstored') }}</span>
    </label>

    <div v-if="loading" class="flex justify-center py-8">
      <Spinner />
    </div>
    <EmptyState v-else-if="filtered.length === 0" :message="t('inventory.storage.addItems.empty')" />
    <ul v-else class="divide-y divide-(--bg-accent) max-h-96 overflow-y-auto max-sm:max-h-none max-sm:flex-1 max-sm:min-h-0">
      <li
          v-for="i in filtered"
          :key="i.id"
          :class="[
            'py-2 px-2 flex items-center gap-3 text-sm cursor-pointer rounded-theme',
            selectedIds.has(i.id) ? 'bg-primary/10' : 'hover:bg-(--bg-accent)',
          ]"
          @click="toggle(i)"
      >
        <font-awesome-icon
            :icon="['fas', selectedIds.has(i.id) ? 'square-check' : 'square']"
            :class="['w-4 shrink-0', selectedIds.has(i.id) ? 'text-primary' : 'text-(--text-muted)']"
        />
        <div class="flex-1 min-w-0 flex flex-col sm:flex-row sm:items-center sm:gap-3">
          <span class="font-medium flex items-center gap-2">
            {{ itemName(i) }}
            <SizeBadge v-if="sizeLabelFor(i)" :lost="!!i.lostAt">{{ sizeLabelFor(i) }}</SizeBadge>
          </span>
          <span v-if="i.internalId" class="text-xs text-(--text-muted)">{{ i.internalId }}</span>
        </div>
        <span class="shrink-0 text-xs text-(--text-muted) text-right max-w-[40%]">{{ locationLabel(i) }}</span>
      </li>
    </ul>

    <div class="flex justify-end gap-2 mt-4 max-sm:mt-auto max-sm:pt-4">
      <SecondaryButton :disabled="submitting" @click="onClose">{{ t('common.cancel') }}</SecondaryButton>
      <PrimaryButton :disabled="submitting || selectedIds.size === 0" :icon="['fas', 'plus']" @click="submit">
        {{ t('inventory.storage.addItems.submit', {count: selectedIds.size}) }}
      </PrimaryButton>
    </div>
  </Modal>
</template>
