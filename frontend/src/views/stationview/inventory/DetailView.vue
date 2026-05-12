/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import type { InventoryDetail, InventoryItem, InventorySize, StationMember } from '@/api/types'
import { inventory, stationMembers } from '@/api'
import { useStations } from '@/composables/useStations'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const { activeStation } = useStations()

const inventoryId = computed(() => Number(route.params.id))
const detail = ref<InventoryDetail | null>(null)
const items = ref<InventoryItem[]>([])
const memberMap = ref<Map<number, StationMember>>(new Map())
const loading = ref(true)
const error = ref('')

const totalCount = computed(() => items.value.length)
const lostCount = computed(() => items.value.filter(i => i.lostAt).length)
const assignedCount = computed(() => items.value.filter(i => i.assignedTo && !i.lostAt).length)
const freeCount = computed(() => items.value.filter(i => !i.assignedTo && !i.lostAt).length)

const lostItems = computed(() => items.value.filter(i => i.lostAt))

const sizeDistribution = computed(() => {
  if (!detail.value?.hasSizes || !detail.value.sizes) return []
  const sizes = detail.value.sizes
  return sizes.map(size => {
    const sizeItems = items.value.filter(i => i.sizeId === size.id)
    return {
      size,
      total: sizeItems.length,
      assigned: sizeItems.filter(i => i.assignedTo && !i.lostAt).length,
      free: sizeItems.filter(i => !i.assignedTo && !i.lostAt).length,
      lost: sizeItems.filter(i => i.lostAt).length,
    }
  })
})

// Items without a size
const noSizeItems = computed(() => {
  if (!detail.value?.hasSizes) return []
  const nosizeItems = items.value.filter(i => !i.sizeId)
  if (nosizeItems.length === 0) return []
  return [{
    size: null as InventorySize | null,
    total: nosizeItems.length,
    assigned: nosizeItems.filter(i => i.assignedTo && !i.lostAt).length,
    free: nosizeItems.filter(i => !i.assignedTo && !i.lostAt).length,
    lost: nosizeItems.filter(i => i.lostAt).length,
  }]
})

const allSizeStats = computed(() => [...sizeDistribution.value, ...noSizeItems.value])

function ownerName(memberId: number | null | undefined): string {
  if (!memberId) return '-'
  const m = memberMap.value.get(memberId)
  return m ? (m.name || m.email || `#${m.id}`) : `#${memberId}`
}

function sizeName(sizeId: number | null | undefined): string {
  if (!sizeId || !detail.value?.sizes) return ''
  return detail.value.sizes.find(s => s.id === sizeId)?.label ?? ''
}

function formatDate(dateStr?: string | null): string {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleDateString('de-DE')
}

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const stationId = activeStation.value?.stationId
    const [inv, allItems, members] = await Promise.all([
      inventory.getInventory(inventoryId.value),
      inventory.listItems(inventoryId.value),
      stationId ? stationMembers.listMembers(stationId) : Promise.resolve([]),
    ])
    detail.value = inv
    items.value = allItems
    const map = new Map<number, StationMember>()
    for (const m of members) map.set(m.id, m)
    memberMap.value = map
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

function goBack() {
  router.push({ name: 'inventory-manage' })
}

onMounted(loadData)
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
        <div>
          <SectionHeader>{{ detail?.name ?? '' }}</SectionHeader>
          <p class="text-sm text-(--text-muted)">
            {{ t('inventory.manage.type.' + (detail?.inventoryType ?? 'internal')) }}
            <span v-if="detail?.hasSizes"> &middot; {{ t('inventory.manage.withSizes') }}</span>
          </p>
        </div>
        <SecondaryButton @click="goBack">
          <font-awesome-icon :icon="['fas', 'chevron-left']" class="mr-1" />
          {{ t('inventory.manage.back') }}
        </SecondaryButton>
      </div>

      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading && detail">
        <!-- Summary -->
        <div class="grid grid-cols-2 sm:grid-cols-4 gap-3">
          <NeutralContainer class="text-center">
            <div class="text-2xl font-bold">{{ totalCount }}</div>
            <div class="text-xs text-(--text-muted)">{{ t('inventory.detail.total') }}</div>
          </NeutralContainer>
          <NeutralContainer class="text-center">
            <div class="text-2xl font-bold text-success">{{ freeCount }}</div>
            <div class="text-xs text-(--text-muted)">{{ t('inventory.detail.free') }}</div>
          </NeutralContainer>
          <NeutralContainer class="text-center">
            <div class="text-2xl font-bold text-primary">{{ assignedCount }}</div>
            <div class="text-xs text-(--text-muted)">{{ t('inventory.detail.assigned') }}</div>
          </NeutralContainer>
          <NeutralContainer class="text-center">
            <div class="text-2xl font-bold text-error">{{ lostCount }}</div>
            <div class="text-xs text-(--text-muted)">{{ t('inventory.detail.lost') }}</div>
          </NeutralContainer>
        </div>

        <!-- Size distribution -->
        <template v-if="detail.hasSizes && allSizeStats.length > 0">
          <SubHeader>{{ t('inventory.detail.bySize') }}</SubHeader>
          <NeutralContainer class="overflow-x-auto">
            <table class="w-full text-sm">
              <thead>
                <tr class="border-b border-bg-light-accent dark:border-bg-dark-accent text-left">
                  <th class="px-3 py-2 font-medium">{{ t('inventory.detail.size') }}</th>
                  <th class="px-3 py-2 font-medium text-center">{{ t('inventory.detail.total') }}</th>
                  <th class="px-3 py-2 font-medium text-center">{{ t('inventory.detail.free') }}</th>
                  <th class="px-3 py-2 font-medium text-center">{{ t('inventory.detail.assigned') }}</th>
                  <th class="px-3 py-2 font-medium text-center">{{ t('inventory.detail.lost') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in allSizeStats" :key="row.size?.id ?? 'none'" class="border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50">
                  <td class="px-3 py-2.5 font-medium">{{ row.size?.label ?? t('inventory.detail.noSize') }}</td>
                  <td class="px-3 py-2.5 text-center">{{ row.total }}</td>
                  <td class="px-3 py-2.5 text-center text-success">{{ row.free }}</td>
                  <td class="px-3 py-2.5 text-center text-primary">{{ row.assigned }}</td>
                  <td class="px-3 py-2.5 text-center text-error">{{ row.lost }}</td>
                </tr>
              </tbody>
            </table>
          </NeutralContainer>
        </template>

        <!-- Lost items -->
        <template v-if="lostItems.length > 0">
          <SubHeader>{{ t('inventory.detail.lostItems') }}</SubHeader>
          <NeutralContainer class="overflow-x-auto">
            <table class="w-full text-sm">
              <thead>
                <tr class="border-b border-bg-light-accent dark:border-bg-dark-accent text-left">
                  <th class="px-3 py-2 font-medium">{{ t('inventory.detail.item') }}</th>
                  <th class="px-3 py-2 font-medium">{{ t('inventory.detail.owner') }}</th>
                  <th class="px-3 py-2 font-medium">{{ t('inventory.detail.lostSince') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in lostItems" :key="item.id" class="border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50">
                  <td class="px-3 py-2.5">
                    <div class="font-medium">
                      {{ item.name }}
                      <span v-if="sizeName(item.sizeId)" class="font-normal text-(--text-muted)">[{{ sizeName(item.sizeId) }}]</span>
                    </div>
                    <div v-if="item.internalId" class="text-xs text-(--text-muted)">{{ item.internalId }}</div>
                  </td>
                  <td class="px-3 py-2.5">{{ ownerName(item.assignedTo) }}</td>
                  <td class="px-3 py-2.5">
                    <ErrorBadge>{{ formatDate(item.lostAt) }}</ErrorBadge>
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
