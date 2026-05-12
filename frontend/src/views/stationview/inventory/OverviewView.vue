/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import type { InventoryItem, InventorySize, StationMember } from '@/api/types'
import { inventory, stationMembers } from '@/api'
import { useStations } from '@/composables/useStations'

const { t } = useI18n()
const { activeStation } = useStations()

interface LostItem {
  item: InventoryItem
  inventoryName: string
  sizeName: string
  ownerName: string
}

const lostItems = ref<LostItem[]>([])
const loading = ref(true)
const error = ref('')

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const stationId = activeStation.value?.stationId
    if (!stationId) return

    const [inventories, members] = await Promise.all([
      inventory.listInventories(),
      stationMembers.listMembers(stationId),
    ])

    const memberMap = new Map<number, StationMember>()
    for (const m of members) memberMap.set(m.id, m)

    const lost: LostItem[] = []
    for (const inv of inventories) {
      const [items, sizes] = await Promise.all([
        inventory.listItems(inv.id),
        inv.hasSizes ? inventory.listSizes(inv.id) : Promise.resolve([]),
      ])
      const sizeMap = new Map<number, InventorySize>()
      for (const s of sizes) sizeMap.set(s.id, s)

      for (const item of items) {
        if (item.lostAt) {
          const owner = item.assignedTo ? memberMap.get(item.assignedTo) : null
          const size = item.sizeId ? sizeMap.get(item.sizeId) : null
          lost.push({
            item,
            inventoryName: inv.name ?? '',
            sizeName: size?.label ?? '',
            ownerName: owner ? (owner.name || owner.email || `#${owner.id}`) : '-',
          })
        }
      }
    }
    lostItems.value = lost
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

function formatDate(dateStr?: string | null): string {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleDateString('de-DE')
}

onMounted(loadData)

// Reload when station becomes available (e.g. after fresh page load)
watch(() => activeStation.value?.stationId, (newId, oldId) => {
  if (newId && !oldId) loadData()
})
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <SectionHeader>{{ t('inventory.overview.title') }}</SectionHeader>

      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading">
        <div v-if="lostItems.length === 0" class="text-center text-(--text-muted) py-8">
          {{ t('inventory.overview.noLost') }}
        </div>

        <template v-else>
          <SubHeader>{{ t('inventory.overview.lost') }}</SubHeader>
          <NeutralContainer class="overflow-x-auto">
            <table class="w-full text-sm">
              <thead>
                <tr class="border-b border-bg-light-accent dark:border-bg-dark-accent text-left">
                  <th class="px-3 py-2 font-medium">{{ t('inventory.overview.colItem') }}</th>
                  <th class="px-3 py-2 font-medium">{{ t('inventory.overview.colOwner') }}</th>
                  <th class="px-3 py-2 font-medium">{{ t('inventory.overview.colLostSince') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="a in lostItems" :key="a.item.id" class="border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50">
                  <td class="px-3 py-2.5">
                    <div class="font-medium">
                      {{ a.item.name }}
                      <span v-if="a.sizeName" class="font-normal text-(--text-muted)">[{{ a.sizeName }}]</span>
                    </div>
                    <div v-if="a.item.internalId" class="text-xs text-(--text-muted)">{{ a.item.internalId }}</div>
                  </td>
                  <td class="px-3 py-2.5">{{ a.ownerName }}</td>
                  <td class="px-3 py-2.5">
                    <ErrorBadge>{{ formatDate(a.item.lostAt) }}</ErrorBadge>
                  </td>
                </tr>
              </tbody>
            </table>
          </NeutralContainer>
        </template>
      </template>
    </div>
  </ViewContent>
</template>
