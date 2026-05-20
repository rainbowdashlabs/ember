/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import SizeBadge from '@/components/badge/SizeBadge.vue'
import type { InventoryItem, InventorySize, StationMember, ExchangeRequestEntry, ProcurementEntry } from '@/api/types'
import { ExchangeStatus } from '@/api/types'
import { inventory, stationMembers, exchanges, procurement } from '@/api'
import { useStations } from '@/composables/useStations'
import MemberName from '@/components/avatar/MemberName.vue'

const { t } = useI18n()
const router = useRouter()
const { activeStation } = useStations()

interface LostItem {
  item: InventoryItem
  inventoryName: string
  sizeName: string
  ownerName: string
}

const lostItems = ref<LostItem[]>([])
const exchangeList = ref<ExchangeRequestEntry[]>([])
const openProcurement = ref<ProcurementEntry[]>([])
const loading = ref(true)
const error = ref('')

const openExchanges = computed(() => exchangeList.value.filter(e => e.status !== ExchangeStatus.EXCHANGED))

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const stationId = activeStation.value?.stationId
    if (!stationId) return

    const [inventories, members, exch, proc] = await Promise.all([
      inventory.listInventories(),
      stationMembers.listMembers(stationId),
      exchanges.listExchanges(),
      procurement.listOpen(),
    ])

    exchangeList.value = exch
    openProcurement.value = proc

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

function exchangeStatusBadge(status: string) {
  switch (status) {
    case ExchangeStatus.EXCHANGED: return SuccessBadge
    case ExchangeStatus.ANNOUNCED: case ExchangeStatus.SHIPPED: return InfoBadge
    case ExchangeStatus.RECEIVED: case ExchangeStatus.ARRIVED: return SecondaryBadge
    default: return SecondaryBadge
  }
}

onMounted(loadData)

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
        <!-- Open exchanges -->
        <div v-if="openExchanges.length > 0" class="space-y-3">
          <SubHeader>
            <font-awesome-icon :icon="['fas', 'rotate']" class="mr-2" />
            {{ t('inventory.overview.exchanges') }} ({{ openExchanges.length }})
          </SubHeader>
          <NeutralContainer class="overflow-x-auto">
            <table class="w-full text-sm">
              <thead>
                <tr class="border-b border-bg-light-accent dark:border-bg-dark-accent text-left">
                  <th class="px-3 py-2 font-medium">{{ t('inventory.overview.colItem') }}</th>
                  <th class="px-3 py-2 font-medium">{{ t('inventory.overview.colOwner') }}</th>
                  <th class="px-3 py-2 font-medium">{{ t('inventory.overview.colStatus') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="ex in openExchanges" :key="ex.id"
                    class="border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50 cursor-pointer hover:bg-(--bg-accent)"
                    @click="router.push({ name: 'inventory-exchanges' })">
                  <td class="px-3 py-2.5">
                    {{ ex.inventoryName }}
                    <SizeBadge>{{ ex.oldSizeLabel ?? t('common.unisize') }} → {{ ex.newSizeLabel ?? t('common.unisize') }}</SizeBadge>
                  </td>
                  <td class="px-3 py-2.5"><MemberName :name="ex.memberName"/></td>
                  <td class="px-3 py-2.5">
                    <component :is="exchangeStatusBadge(ex.status)">{{ t(`exchanges.status.${ex.status}`) }}</component>
                  </td>
                </tr>
              </tbody>
            </table>
          </NeutralContainer>
        </div>

        <!-- Open procurement -->
        <div v-if="openProcurement.length > 0" class="space-y-3">
          <SubHeader>
            <font-awesome-icon :icon="['fas', 'folder-plus']" class="mr-2" />
            {{ t('inventory.overview.procurement') }} ({{ openProcurement.length }})
          </SubHeader>
          <NeutralContainer class="overflow-x-auto">
            <table class="w-full text-sm">
              <thead>
                <tr class="border-b border-bg-light-accent dark:border-bg-dark-accent text-left">
                  <th class="px-3 py-2 font-medium">{{ t('inventory.overview.colItem') }}</th>
                  <th class="px-3 py-2 font-medium">{{ t('inventory.overview.colOwner') }}</th>
                  <th class="px-3 py-2 font-medium">{{ t('inventory.overview.colNotes') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="p in openProcurement" :key="p.id"
                    class="border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50 cursor-pointer hover:bg-(--bg-accent)"
                    @click="router.push({ name: 'inventory-procurement' })">
                  <td class="px-3 py-2.5">
                    {{ p.inventoryName }}
                    <SizeBadge>{{ p.sizeLabel || t('common.unisize') }}</SizeBadge>
                  </td>
                  <td class="px-3 py-2.5"><MemberName :name="p.memberName"/></td>
                  <td class="px-3 py-2.5 text-(--text-muted)">{{ p.notes || '-' }}</td>
                </tr>
              </tbody>
            </table>
          </NeutralContainer>
        </div>

        <!-- Lost items -->
        <div v-if="lostItems.length > 0" class="space-y-3">
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
                      <SizeBadge lost>{{ a.sizeName || t('common.unisize') }}</SizeBadge>
                    </div>
                    <div v-if="a.item.internalId" class="text-xs text-(--text-muted)">{{ a.item.internalId }}</div>
                  </td>
                  <td class="px-3 py-2.5"><MemberName :name="a.ownerName"/></td>
                  <td class="px-3 py-2.5">
                    <ErrorBadge>{{ formatDate(a.item.lostAt) }}</ErrorBadge>
                  </td>
                </tr>
              </tbody>
            </table>
          </NeutralContainer>
        </div>

        <div v-if="lostItems.length === 0 && openExchanges.length === 0 && openProcurement.length === 0"
             class="text-center text-(--text-muted) py-8">
          {{ t('inventory.overview.noLost') }}
        </div>
      </template>
    </div>
  </ViewContent>
</template>
