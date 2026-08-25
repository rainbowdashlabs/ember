/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import {ExchangeStatus, type ExchangeRequestEntry} from '@/api/exchanges'
import type { Inventory, InventorySize } from '@/api/inventory'
import type { ProcurementEntry } from '@/api/procurement'
import type { StationMember } from '@/api/types'
import { inventory, stationMembers, exchanges, procurement } from '@/api'
import { useStations } from '@/composables/useStations'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import EmptyState from '@/components/feedback/EmptyState.vue'
import OverviewExchangesSection from './overviewview/OverviewExchangesSection.vue'
import OverviewProcurementSection from './overviewview/OverviewProcurementSection.vue'
import OverviewLostSection from './overviewview/OverviewLostSection.vue'
import RequestFromOwnerPanel from './overviewview/RequestFromOwnerPanel.vue'
import type { LostItem } from './overviewview/types'

const { t } = useI18n()
const { activeStation } = useStations()

const lostItems = ref<LostItem[]>([])
const exchangeList = ref<ExchangeRequestEntry[]>([])
const openProcurement = ref<ProcurementEntry[]>([])
const inventoryTypeMap = ref<Map<number, string>>(new Map())
const inventoryList = ref<Inventory[]>([])
/** The association above that keeps its gear here, which is who a request would go to. */
const ownerAbove = ref<string | null>(null)

const openExchanges = computed(() => exchangeList.value
    .filter(e => e.status !== ExchangeStatus.DONE)
    .slice()
    .sort((a, b) => a.createdAt.localeCompare(b.createdAt)))

const openProcurementSorted = computed(() => openProcurement.value
    .slice()
    .sort((a, b) => a.requestedAt.localeCompare(b.requestedAt)))

const lostItemsSorted = computed(() => lostItems.value
    .slice()
    .sort((a, b) => (a.item.lostAt ?? '').localeCompare(b.item.lostAt ?? '')))

const {loading, error, reload} = useAsyncLoader(async () => {
  const stationId = activeStation.value?.stationId
  if (!stationId) return

  const [inventories, members, exch, proc, owner] = await Promise.all([
    inventory.listInventories(),
    stationMembers.listMembers(),
    exchanges.listExchanges(),
    procurement.listOpen(),
    inventory.ownerAbove(),
  ])

  exchangeList.value = exch
  openProcurement.value = proc
  inventoryList.value = inventories
  ownerAbove.value = owner

  const typeMap = new Map<number, string>()
  for (const inv of inventories) typeMap.set(inv.id, inv.inventoryType ?? '')
  inventoryTypeMap.value = typeMap

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
          inventoryType: inv.inventoryType ?? '',
          sizeName: size?.label ?? '',
          ownerName: owner ? (owner.name || owner.email || `#${owner.id}`) : '-',
          ownerIdentity: owner?.identity ?? null,
        })
      }
    }
  }
  lostItems.value = lost
})

watch(() => activeStation.value?.stationId, (newId, oldId) => {
  if (newId && !oldId) reload()
})
</script>

<template>
  <ViewContent
      :title="t('pages.inventory-overview.title')"
      :subtitle="t('pages.inventory-overview.subtitle')"
  >
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading">
        <RequestFromOwnerPanel :owner-name="ownerAbove" :inventories="inventoryList" @requested="reload" />
        <OverviewExchangesSection v-if="openExchanges.length > 0" :exchanges="openExchanges" />
        <OverviewProcurementSection v-if="openProcurement.length > 0" :entries="openProcurementSorted" :inventory-type-map="inventoryTypeMap" />
        <OverviewLostSection v-if="lostItems.length > 0" :items="lostItemsSorted" />
        <EmptyState v-if="lostItems.length === 0 && openExchanges.length === 0 && openProcurement.length === 0">{{ t('inventory.overview.noLost') }}</EmptyState>
      </template>
    </div>
  </ViewContent>
</template>
