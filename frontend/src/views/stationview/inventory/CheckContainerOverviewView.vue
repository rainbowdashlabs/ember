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
import PageHeader from '@/components/typography/PageHeader.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Alert from '@/components/feedback/Alert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import {inventoryContainers} from '@/api'
import type {InventoryContainer, InventoryContainerKind} from '@/api/inventoryContainers'

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

const filtered = computed(() => {
  const term = search.value.trim().toLowerCase()
  if (!term) return containers.value
  return containers.value.filter(c =>
      c.name.toLowerCase().includes(term)
      || (c.internalId ?? '').toLowerCase().includes(term)
      || (c.description ?? '').toLowerCase().includes(term))
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
    error.value = e?.response?.data?.message ?? t('inventory.checkContainer.loadError')
  } finally {
    loading.value = false
  }
}

function startCheck(c: InventoryContainer) {
  router.push({name: 'inventory-check-container-walk', params: {id: String(c.id)}})
}

onMounted(load)
</script>

<template>
  <ViewContent>
    <PageHeader>{{ t('inventory.checkContainer.title') }}</PageHeader>
    <p class="text-sm text-(--text-muted) mb-4">{{ t('inventory.checkContainer.intro') }}</p>

    <NeutralContainer class="mb-4">
      <TextInput v-model="search" :placeholder="t('inventory.checkContainer.searchPlaceholder')" />
    </NeutralContainer>

    <Alert v-if="error" variant="error" class="mb-4">{{ error }}</Alert>

    <div v-if="loading" class="flex justify-center py-12">
      <Spinner size="lg" />
    </div>
    <template v-else>
      <NeutralContainer>
        <EmptyState v-if="filtered.length === 0" :message="t('inventory.checkContainer.noContainers')" />
        <ul v-else class="divide-y divide-(--bg-accent)">
          <li
              v-for="c in filtered"
              :key="c.id"
              class="py-2 flex items-center gap-3"
          >
            <font-awesome-icon :icon="['fas', kindById.get(c.kindId ?? -1)?.icon ?? 'box']" class="w-4 text-(--text-muted)" />
            <span class="flex-1 font-medium">{{ c.name }}</span>
            <span v-if="c.internalId" class="text-xs text-(--text-muted)">{{ c.internalId }}</span>
            <PrimaryButton size="sm" @click="startCheck(c)">
              <font-awesome-icon :icon="['fas', 'clipboard-check']" class="mr-2" />
              {{ t('inventory.checkContainer.start') }}
            </PrimaryButton>
          </li>
        </ul>
      </NeutralContainer>
    </template>
  </ViewContent>
</template>
