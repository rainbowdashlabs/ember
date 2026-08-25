/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useInventoryRoutes} from '@/composables/useInventoryRoutes'
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Alert from '@/components/feedback/Alert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SearchInput from '@/components/input/text/SearchInput.vue'
import ContainerTree from '@/views/stationview/inventory/storageview/ContainerTree.vue'
import {inventoryContainers} from '@/api'
import type {InventoryContainer, InventoryContainerKind} from '@/api/inventoryContainers'
import {apiErrorMessage} from '@/util/apiError'

const routes = useInventoryRoutes()

const {t} = useI18n()
const router = useRouter()

const containers = ref<InventoryContainer[]>([])
const kinds = ref<InventoryContainerKind[]>([])
const loading = ref(true)
const error = ref('')
const search = ref('')

const kindById = computed(() => {
  const map = new Map<number, InventoryContainerKind>()
  for (const k of kinds.value) map.set(k.id, k)
  return map
})

const childrenByParent = computed(() => {
  const map = new Map<number, InventoryContainer[]>()
  for (const c of containers.value) {
    if (c.parentId == null) continue
    const list = map.get(c.parentId) ?? []
    list.push(c)
    map.set(c.parentId, list)
  }
  for (const list of map.values()) {
    list.sort((a, b) => a.name.localeCompare(b.name))
  }
  return map
})

const roots = computed(() => containers.value
    .filter(c => c.parentId == null)
    .sort((a, b) => a.name.localeCompare(b.name)))

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
  } catch (e) {
    error.value = apiErrorMessage(e) ?? t('inventory.checkContainer.loadError')
  } finally {
    loading.value = false
  }
}

function startCheck(c: InventoryContainer) {
  router.push({name: routes.checkContainerWalk, params: {id: String(c.id)}})
}

onMounted(load)
</script>

<template>
  <ViewContent
      :title="t('pages.inventory-check-container-overview.title')"
      :subtitle="t('pages.inventory-check-container-overview.subtitle')"
  >
    <slot name="before"/>

    <NeutralContainer class="mb-4">
      <SearchInput v-model="search" :placeholder="t('inventory.checkContainer.searchPlaceholder')" />
    </NeutralContainer>

    <Alert v-if="error" variant="error" class="mb-4">{{ error }}</Alert>

    <div v-if="loading" class="flex justify-center py-12">
      <Spinner size="lg" />
    </div>
    <template v-else>
      <NeutralContainer>
        <EmptyState v-if="roots.length === 0" :message="t('inventory.checkContainer.noContainers')" />
        <ContainerTree
            v-else
            :roots="roots"
            :children-by-parent="childrenByParent"
            :kind-by-id="kindById"
            :search="search"
            @open="startCheck"
        />
      </NeutralContainer>
    </template>
  </ViewContent>
</template>
