/*
*     SPDX-License-Identifier: AGPL-3.0-only
*
*     Copyright (C) RainbowDashLabs and Contributor
*/
<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import {inventory} from '@/api'
import type {MyInventoryItem, MyRequirement} from '@/api/inventory'

const {t} = useI18n()

const items = ref<MyInventoryItem[]>([])
const requirements = ref<MyRequirement[]>([])
const loading = ref(true)
const error = ref('')

interface InventoryGroup {
  inventoryId: number
  inventoryName: string
  requiredQuantity: number
  items: MyInventoryItem[]
}

const grouped = computed((): InventoryGroup[] => {
  // Build groups based on requirements order
  const groups: InventoryGroup[] = []
  const usedInventoryIds = new Set<number>()

  for (const req of requirements.value) {
    const groupItems = items.value.filter(i => i.inventoryId === req.inventoryId)
    groups.push({
      inventoryId: req.inventoryId,
      inventoryName: req.inventoryName,
      requiredQuantity: req.requiredQuantity,
      items: groupItems,
    })
    usedInventoryIds.add(req.inventoryId)
  }

  // Items not covered by any requirement (extra items)
  const extraItems = items.value.filter(i => !usedInventoryIds.has(i.inventoryId))
  if (extraItems.length > 0) {
    const extraByInv = new Map<number, MyInventoryItem[]>()
    for (const item of extraItems) {
      const list = extraByInv.get(item.inventoryId) ?? []
      list.push(item)
      extraByInv.set(item.inventoryId, list)
    }
    for (const [invId, invItems] of extraByInv) {
      groups.push({
        inventoryId: invId,
        inventoryName: invItems[0].inventoryName,
        requiredQuantity: 0,
        items: invItems,
      })
    }
  }

  return groups
})

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const [myItems, myReqs] = await Promise.all([
      inventory.myItems(),
      inventory.myRequirements(),
    ])
    items.value = myItems
    requirements.value = myReqs
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <SectionHeader>{{ t('profile.inventory') }}</SectionHeader>

      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading">
        <div v-if="grouped.length === 0 && items.length === 0" class="text-center text-(--text-muted) py-8">
          {{ t('profile.noInventory') }}
        </div>

        <div v-else class="space-y-6">
          <div v-for="group in grouped" :key="group.inventoryId">
            <div class="flex items-center justify-between mb-2">
              <SubHeader>{{ group.inventoryName }}</SubHeader>
              <span v-if="group.requiredQuantity > 0" class="text-sm text-(--text-muted)">
                {{ group.items.length }} / {{ group.requiredQuantity }}
                <span v-if="group.items.length < group.requiredQuantity" class="text-error">
                  ({{ group.requiredQuantity - group.items.length }} fehlt)
                </span>
              </span>
            </div>

            <div v-if="group.items.length === 0" class="text-sm text-(--text-muted) py-2">
              {{ t('profile.noInventory') }}
            </div>

            <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2">
              <NeutralContainer
                  v-for="item in group.items"
                  :key="item.id"
                  :class="item.lostAt ? 'opacity-60 border-error' : ''"
              >
                <div class="font-medium text-sm">
                  {{ item.name }}
                  <span v-if="item.sizeName" class="font-normal text-(--text-muted)">[{{ item.sizeName }}]</span>
                </div>
                <div v-if="item.internalId" class="text-xs text-(--text-muted)">{{ item.internalId }}</div>
                <ErrorBadge v-if="item.lostAt" class="mt-1">
                  {{ t('profile.lostSince') }} {{ new Date(item.lostAt).toLocaleDateString('de-DE') }}
                </ErrorBadge>
              </NeutralContainer>
            </div>
          </div>
        </div>
      </template>
    </div>
  </ViewContent>
</template>
