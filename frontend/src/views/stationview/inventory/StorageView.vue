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
import IconButton from '@/components/button/IconButton.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import ContainerNewModal from '@/views/stationview/inventory/storageview/ContainerNewModal.vue'
import ContainerTree from '@/views/stationview/inventory/storageview/ContainerTree.vue'
import {inventoryContainers} from '@/api'
import type {InventoryContainer, InventoryContainerKind} from '@/api/inventoryContainers'

const {t} = useI18n()
const router = useRouter()

const containers = ref<InventoryContainer[]>([])
const kinds = ref<InventoryContainerKind[]>([])
const loading = ref(true)
const error = ref('')
const search = ref('')
const scanValue = ref('')
const showNewModal = ref(false)

const kindById = computed(() => {
  const map = new Map<number, InventoryContainerKind>()
  for (const k of kinds.value) map.set(k.id, k)
  return map
})

const filtered = computed(() => {
  const term = search.value.trim().toLowerCase()
  if (!term) return containers.value
  return containers.value.filter(c =>
      c.name.toLowerCase().includes(term)
      || (c.internalId ?? '').toLowerCase().includes(term)
      || (c.description ?? '').toLowerCase().includes(term))
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
    const [c, k] = await Promise.all([
      inventoryContainers.listContainers(),
      inventoryContainers.listKinds(),
    ])
    containers.value = c
    kinds.value = k
  } catch (e: any) {
    error.value = e?.response?.data?.message ?? t('inventory.storage.loadError')
  } finally {
    loading.value = false
  }
}

async function handleScan() {
  const term = scanValue.value.trim()
  if (!term) return
  const direct = await inventoryContainers.resolveContainerByScan(term)
  if (direct) {
    scanValue.value = ''
    router.push({name: 'inventory-container-detail', params: {id: String(direct.id)}})
    return
  }
  error.value = t('inventory.storage.scanNoMatch', {scan: term})
}

function openDetail(c: InventoryContainer) {
  router.push({name: 'inventory-container-detail', params: {id: String(c.id)}})
}

async function onCreated() {
  showNewModal.value = false
  await load()
}

onMounted(load)
</script>

<template>
  <ViewContent>
    <div v-if="error" class="mb-4">
      <Alert variant="error">{{ error }}</Alert>
    </div>

    <NeutralContainer class="mb-4">
      <div class="flex flex-wrap items-center gap-3">
        <div class="flex-1 min-w-48">
          <TextInput v-model="search" :placeholder="t('inventory.storage.searchPlaceholder')" />
        </div>
        <div class="flex items-center gap-2">
          <TextInput v-model="scanValue" :placeholder="t('inventory.storage.scanPlaceholder')" @keydown.enter="handleScan" />
          <IconButton :icon="['fas', 'barcode']" :label="t('inventory.storage.scanAction')" @click="handleScan" />
        </div>
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
            @open="openDetail"
        />
      </NeutralContainer>

      <template v-if="search">
        <SectionHeader>{{ t('inventory.storage.searchResults') }}</SectionHeader>
        <NeutralContainer>
          <ul class="divide-y divide-(--bg-accent)">
            <li v-for="c in filtered" :key="c.id" class="py-2 flex items-center gap-3 cursor-pointer hover:bg-(--bg-accent) rounded-theme px-2" @click="openDetail(c)">
              <font-awesome-icon :icon="['fas', kindById.get(c.kindId ?? -1)?.icon ?? 'box']" class="w-4 text-(--text-muted)" />
              <span class="font-medium">{{ c.name }}</span>
              <span v-if="c.internalId" class="text-xs text-(--text-muted)">{{ c.internalId }}</span>
            </li>
            <li v-if="filtered.length === 0" class="py-3 text-sm text-(--text-muted)">{{ t('inventory.storage.noResults') }}</li>
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
