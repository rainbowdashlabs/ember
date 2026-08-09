/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SearchInput from '@/components/input/text/SearchInput.vue'
import ScanButton from '@/components/scanner/ScanButton.vue'
import {normaliseScannedPayload} from '@/components/scanner/useBarcodeScanner'
import ContainerNewModal from '@/views/stationview/inventory/storageview/ContainerNewModal.vue'
import ContainerTree from '@/views/stationview/inventory/storageview/ContainerTree.vue'
import {inventory, inventoryContainers} from '@/api'
import {containerPathFor} from '@/util/containerPath'
import type {InventoryContainer, InventoryContainerKind} from '@/api/inventoryContainers'
import type {InventoryItem} from '@/api/inventory'
import {apiErrorMessage} from '@/util/apiError'

const {t} = useI18n()
const router = useRouter()

const containers = ref<InventoryContainer[]>([])
const items = ref<InventoryItem[]>([])
const kinds = ref<InventoryContainerKind[]>([])
const loading = ref(true)
const error = ref('')
const search = ref('')
const showNewModal = ref(false)

const kindById = computed(() => {
  const map = new Map<number, InventoryContainerKind>()
  for (const k of kinds.value) map.set(k.id, k)
  return map
})

const containerById = computed(() => {
  const map = new Map<number, InventoryContainer>()
  for (const c of containers.value) map.set(c.id, c)
  return map
})

function pathFor(containerId: number | null | undefined): string {
  return containerPathFor(containerById.value, containerId)
}

const containerMatches = computed(() => {
  const term = search.value.trim().toLowerCase()
  if (!term) return []
  return containers.value.filter(c =>
      c.name.toLowerCase().includes(term)
      || (c.internalId ?? '').toLowerCase().includes(term)
      || (c.description ?? '').toLowerCase().includes(term))
})

const itemMatches = computed(() => {
  const term = search.value.trim().toLowerCase()
  if (!term) return []
  return items.value.filter(i =>
      (i.name ?? '').toLowerCase().includes(term)
      || (i.internalId ?? '').toLowerCase().includes(term))
})

const roots = computed(() => containers.value.filter(c => !c.parentId))
const childrenByParent = computed(() => {
  const map = new Map<number, InventoryContainer[]>()
  for (const c of containers.value) {
    if (!c.parentId) continue
    const list = map.get(c.parentId) ?? []
    list.push(c)
    map.set(c.parentId, list)
  }
  for (const list of map.values()) list.sort((a, b) => a.name.localeCompare(b.name))
  return map
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [c, k, allItems] = await Promise.all([
      inventoryContainers.listContainers(),
      inventoryContainers.listKinds(),
      inventory.listAllItems(),
    ])
    containers.value = c
    kinds.value = k
    items.value = allItems
  } catch (e) {
    error.value = apiErrorMessage(e) ?? t('inventory.storage.loadError')
  } finally {
    loading.value = false
  }
}

async function onScanDecoded(value: string) {
  const term = normaliseScannedPayload(value).trim()
  if (!term) return
  const container = await inventoryContainers.resolveContainerByScan(term)
  if (container) {
    router.push({name: 'inventory-container-detail', params: {id: String(container.id)}})
    return
  }
  const item = await inventory.findByInternalId(term)
  if (item) {
    router.push({name: 'inventory-item-detail', params: {id: String(item.id)}})
    return
  }
  error.value = t('inventory.storage.scanNoMatch', {scan: term})
}

function openContainer(c: InventoryContainer) {
  router.push({name: 'inventory-container-detail', params: {id: String(c.id)}})
}

function openItem(i: InventoryItem) {
  router.push({name: 'inventory-item-detail', params: {id: String(i.id)}})
}

async function onCreated() {
  showNewModal.value = false
  await load()
}

onMounted(load)
</script>

<template>
  <ViewContent
      :title="t('pages.inventory-storage.title')"
      :subtitle="t('pages.inventory-storage.subtitle')"
  >
    <div v-if="error" class="mb-4">
      <Alert variant="error">{{ error }}</Alert>
    </div>

    <NeutralContainer class="mb-4">
      <div class="flex flex-wrap items-center gap-3">
        <div class="flex-1 min-w-48">
          <SearchInput v-model="search" :placeholder="t('inventory.storage.searchPlaceholder')" />
        </div>
        <ScanButton @decoded="onScanDecoded" />
        <PrimaryButton @click="showNewModal = true">
          <font-awesome-icon :icon="['fas', 'plus']" class="mr-2" />
          {{ t('inventory.storage.newContainer') }}
        </PrimaryButton>
      </div>
    </NeutralContainer>

    <div v-if="loading" class="flex justify-center py-12">
      <Spinner size="lg" />
    </div>
    <template v-else>
      <SectionHeader>{{ t('inventory.storage.treeTitle') }}</SectionHeader>
      <NeutralContainer class="mb-6">
        <div v-if="roots.length === 0" class="py-6">
          <EmptyState :message="t('inventory.storage.empty')" />
        </div>
        <ContainerTree
            v-else
            :roots="roots"
            :children-by-parent="childrenByParent"
            :kind-by-id="kindById"
            :search="search"
            @open="openContainer"
        />
      </NeutralContainer>

      <template v-if="search">
        <SectionHeader>{{ t('inventory.storage.searchResults') }}</SectionHeader>
        <NeutralContainer>
          <div v-if="containerMatches.length === 0 && itemMatches.length === 0" class="py-3 text-sm text-(--text-muted)">
            {{ t('inventory.storage.noResults') }}
          </div>
          <ul v-else class="divide-y divide-(--bg-accent)">
            <li
                v-for="c in containerMatches"
                :key="`c-${c.id}`"
                class="py-2 flex items-center gap-3 cursor-pointer hover:bg-(--bg-accent) rounded-theme px-2"
                @click="openContainer(c)"
            >
              <font-awesome-icon :icon="['fas', kindById.get(c.kindId ?? -1)?.icon ?? 'box']" class="w-4 text-(--text-muted)" />
              <span class="font-medium">{{ c.name }}</span>
              <span v-if="c.internalId" class="text-xs text-(--text-muted)">{{ c.internalId }}</span>
              <span class="ml-auto text-xs text-(--text-muted)">{{ t('inventory.storage.searchKindContainer') }}</span>
            </li>
            <li
                v-for="i in itemMatches"
                :key="`i-${i.id}`"
                class="py-2 flex flex-col gap-1 cursor-pointer hover:bg-(--bg-accent) rounded-theme px-2"
                @click="openItem(i)"
            >
              <div class="flex items-center gap-3">
                <font-awesome-icon :icon="['fas', 'cube']" class="w-4 text-(--text-muted)" />
                <span class="font-medium">{{ i.name ?? '' }}</span>
                <span v-if="i.internalId" class="text-xs text-(--text-muted)">{{ i.internalId }}</span>
                <span class="ml-auto text-xs text-(--text-muted)">{{ t('inventory.storage.searchKindItem') }}</span>
              </div>
              <div class="pl-7 text-xs text-(--text-muted) truncate">
                <template v-if="i.containerId">
                  <font-awesome-icon :icon="['fas', 'location-dot']" class="mr-1.5" />
                  {{ pathFor(i.containerId) }}
                </template>
                <template v-else>
                  <font-awesome-icon :icon="['fas', 'circle-question']" class="mr-1.5" />
                  {{ t('inventory.storage.itemPathUnassigned') }}
                </template>
              </div>
            </li>
          </ul>
        </NeutralContainer>
      </template>
    </template>

    <ContainerNewModal
        v-if="showNewModal"
        :kinds="kinds"
        :containers="containers"
        @created="onCreated"
        @close="showNewModal = false"
    />
  </ViewContent>
</template>
