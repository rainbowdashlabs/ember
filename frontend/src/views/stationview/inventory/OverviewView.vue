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
import { useBreakpoint } from '@/composables/useBreakpoint'
import MemberName from '@/components/avatar/MemberName.vue'
import Th from '@/components/table/Th.vue'
import Td from '@/components/table/Td.vue'
import THead from '@/components/table/THead.vue'
import TRow from '@/components/table/TRow.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'

const { t } = useI18n()
const router = useRouter()
const { activeStation } = useStations()
const { isMobile } = useBreakpoint()

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
      stationMembers.listMembers(),
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
          <!-- Mobile cards -->
          <div v-if="isMobile" class="space-y-2">
            <NeutralContainer v-for="ex in openExchanges" :key="ex.id" class="space-y-1 cursor-pointer" @click="router.push({ name: 'inventory-exchanges' })">
              <div class="flex items-center justify-between">
                <span class="text-sm font-medium">{{ ex.inventoryName }}</span>
                <component :is="exchangeStatusBadge(ex.status)">{{ t(`exchanges.status.${ex.status}`) }}</component>
              </div>
              <div class="text-xs text-(--text-muted)"><MemberName :name="ex.memberName"/></div>
              <SizeBadge>{{ ex.oldSizeLabel ?? t('common.unisize') }} &rarr; {{ ex.newSizeLabel ?? t('common.unisize') }}</SizeBadge>
            </NeutralContainer>
          </div>
          <!-- Desktop table -->
          <NeutralContainer v-else class="overflow-x-auto">
            <table class="w-full text-sm">
              <thead>
                <THead>
                  <Th>{{ t('inventory.overview.colItem') }}</Th>
                  <Th>{{ t('inventory.overview.colOwner') }}</Th>
                  <Th>{{ t('inventory.overview.colStatus') }}</Th>
                </THead>
              </thead>
              <tbody>
                <TRow v-for="ex in openExchanges" :key="ex.id"
                    class="cursor-pointer hover:bg-(--bg-accent)"
                    @click="router.push({ name: 'inventory-exchanges' })">
                  <Td>
                    {{ ex.inventoryName }}
                    <SizeBadge>{{ ex.oldSizeLabel ?? t('common.unisize') }} &rarr; {{ ex.newSizeLabel ?? t('common.unisize') }}</SizeBadge>
                  </Td>
                  <Td><MemberName :name="ex.memberName"/></Td>
                  <Td>
                    <component :is="exchangeStatusBadge(ex.status)">{{ t(`exchanges.status.${ex.status}`) }}</component>
                  </Td>
                </TRow>
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
          <!-- Mobile cards -->
          <div v-if="isMobile" class="space-y-2">
            <NeutralContainer v-for="p in openProcurement" :key="p.id" class="space-y-1 cursor-pointer" @click="router.push({ name: 'inventory-procurement' })">
              <div class="flex items-center justify-between">
                <span class="text-sm font-medium">{{ p.inventoryName }}</span>
                <SizeBadge>{{ p.sizeLabel || t('common.unisize') }}</SizeBadge>
              </div>
              <div class="text-xs text-(--text-muted)"><MemberName :name="p.memberName"/></div>
              <div v-if="p.notes" class="text-xs text-(--text-muted)">{{ p.notes }}</div>
            </NeutralContainer>
          </div>
          <!-- Desktop table -->
          <NeutralContainer v-else class="overflow-x-auto">
            <table class="w-full text-sm">
              <thead>
                <THead>
                  <Th>{{ t('inventory.overview.colItem') }}</Th>
                  <Th>{{ t('inventory.overview.colOwner') }}</Th>
                  <Th>{{ t('inventory.overview.colNotes') }}</Th>
                </THead>
              </thead>
              <tbody>
                <TRow v-for="p in openProcurement" :key="p.id"
                    class="cursor-pointer hover:bg-(--bg-accent)"
                    @click="router.push({ name: 'inventory-procurement' })">
                  <Td>
                    {{ p.inventoryName }}
                    <SizeBadge>{{ p.sizeLabel || t('common.unisize') }}</SizeBadge>
                  </Td>
                  <Td><MemberName :name="p.memberName"/></Td>
                  <Td muted>{{ p.notes || '-' }}</Td>
                </TRow>
              </tbody>
            </table>
          </NeutralContainer>
        </div>

        <!-- Lost items -->
        <div v-if="lostItems.length > 0" class="space-y-3">
          <SubHeader>{{ t('inventory.overview.lost') }}</SubHeader>
          <!-- Mobile cards -->
          <div v-if="isMobile" class="space-y-2">
            <NeutralContainer v-for="a in lostItems" :key="a.item.id" class="space-y-1">
              <div class="flex items-center justify-between">
                <span class="text-sm font-medium">{{ a.item.name }}</span>
                <ErrorBadge>{{ formatDate(a.item.lostAt) }}</ErrorBadge>
              </div>
              <div class="flex items-center gap-2">
                <SizeBadge lost>{{ a.sizeName || t('common.unisize') }}</SizeBadge>
                <span v-if="a.item.internalId" class="text-xs text-(--text-muted)">{{ a.item.internalId }}</span>
              </div>
              <div class="text-xs text-(--text-muted)"><MemberName :name="a.ownerName"/></div>
            </NeutralContainer>
          </div>
          <!-- Desktop table -->
          <NeutralContainer v-else class="overflow-x-auto">
            <table class="w-full text-sm">
              <thead>
                <THead>
                  <Th>{{ t('inventory.overview.colItem') }}</Th>
                  <Th>{{ t('inventory.overview.colOwner') }}</Th>
                  <Th>{{ t('inventory.overview.colLostSince') }}</Th>
                </THead>
              </thead>
              <tbody>
                <TRow v-for="a in lostItems" :key="a.item.id">
                  <Td>
                    <div class="font-medium">
                      {{ a.item.name }}
                      <SizeBadge lost>{{ a.sizeName || t('common.unisize') }}</SizeBadge>
                    </div>
                    <div v-if="a.item.internalId" class="text-xs text-(--text-muted)">{{ a.item.internalId }}</div>
                  </Td>
                  <Td><MemberName :name="a.ownerName"/></Td>
                  <Td>
                    <ErrorBadge>{{ formatDate(a.item.lostAt) }}</ErrorBadge>
                  </Td>
                </TRow>
              </tbody>
            </table>
          </NeutralContainer>
        </div>

        <EmptyState v-if="lostItems.length === 0 && openExchanges.length === 0 && openProcurement.length === 0">{{ t('inventory.overview.noLost') }}</EmptyState>
      </template>
    </div>
  </ViewContent>
</template>
