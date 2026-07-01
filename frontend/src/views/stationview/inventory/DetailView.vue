/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import type { InventoryDetail, InventoryItem, InventorySize, StationMember, ProcurementEntry } from '@/api/types'
import { InventoryTypes, StationPermission } from '@/api/types'
import { inventory, inventoryContainers, stationMembers, procurement } from '@/api'
import type { InventoryContainer } from '@/api/inventoryContainers'
import { useSession } from '@/composables/useSession'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import { getLentOutByInventory, type LentOutItem } from '@/api/lending'
import { useStations } from '@/composables/useStations'
import DetailHeader from './detailview/DetailHeader.vue'
import DetailLoadedContent from './detailview/DetailLoadedContent.vue'
import DetailViewModals from './detailview/DetailViewModals.vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const { currentStationId } = useStations()
const { hasPermission } = useSession()

const inventoryId = computed(() => Number(route.params.id))
const detail = ref<InventoryDetail | null>(null)
const items = ref<InventoryItem[]>([])
const memberMap = ref<Map<number, StationMember>>(new Map())
const openProcurement = ref<ProcurementEntry[]>([])
const lentOutItems = ref<LentOutItem[]>([])
const containers = ref<InventoryContainer[]>([])
const modals = ref<InstanceType<typeof DetailViewModals> | null>(null)

const containerPathById = computed(() => {
  const byId = new Map<number, InventoryContainer>()
  for (const c of containers.value) byId.set(c.id, c)
  const cache = new Map<number, string>()
  function pathOf(id: number, seen: Set<number>): string {
    if (cache.has(id)) return cache.get(id)!
    const c = byId.get(id)
    if (!c) return ''
    if (seen.has(id)) return c.name
    seen.add(id)
    const parent = c.parentId ? pathOf(c.parentId, seen) : ''
    const path = parent ? `${parent} / ${c.name}` : c.name
    cache.set(id, path)
    return path
  }
  const out = new Map<number, string>()
  for (const c of containers.value) out.set(c.id, pathOf(c.id, new Set()))
  return out
})

const canEdit = computed(() => hasPermission(StationPermission.INVENTORY_EDIT))
const canProcure = computed(() => hasPermission(StationPermission.INVENTORY_PROCUREMENT))
const canCreateInternal = computed(() => hasPermission(StationPermission.INVENTORY_CREATE_INTERNAL))
const canCreateExternal = computed(() => hasPermission(StationPermission.INVENTORY_CREATE_EXTERNAL))

const permissions = computed(() => {
  const type = detail.value?.inventoryType ?? InventoryTypes.INTERNAL
  const canCreateItem = type === InventoryTypes.INTERNAL ? canCreateInternal.value
    : type === InventoryTypes.EXTERNAL ? canCreateExternal.value
    : canCreateInternal.value || canCreateExternal.value
  return {
    canEdit: canEdit.value,
    canProcure: canProcure.value,
    canCreateItem,
    canQuickAssign: (type === InventoryTypes.EXTERNAL || type === InventoryTypes.MIXED) && canCreateExternal.value,
    canAddInternal: type !== InventoryTypes.EXTERNAL && canCreateInternal.value,
  }
})

const activeLendingStatuses = new Set(['APPROVED', 'LENT'])

const lentItemStationMap = computed(() => {
  const map = new Map<number, string>()
  for (const l of lentOutItems.value) {
    if (l.assignedItemId && activeLendingStatuses.has(l.status)) {
      map.set(l.assignedItemId, l.requestingStationName)
    }
  }
  return map
})

function isLentOut(itemId: number): boolean {
  return lentItemStationMap.value.has(itemId)
}

const lostItems = computed(() => items.value.filter(i => i.lostAt))

const counts = computed(() => ({
  total: items.value.length,
  lost: lostItems.value.length,
  lentOut: lentItemStationMap.value.size,
  assigned: items.value.filter(i => i.assignedTo && !i.lostAt && !isLentOut(i.id)).length,
  free: items.value.filter(i => !i.assignedTo && !i.lostAt && !isLentOut(i.id)).length,
}))

const freeItems = computed(() => items.value.filter(i => !i.assignedTo && !i.lostAt && !isLentOut(i.id)))
const unassignedMembers = computed(() => [...memberMap.value.values()])

const sizeDistribution = computed(() => {
  if (!detail.value?.hasSizes || !detail.value.sizes) return []
  return detail.value.sizes.map(size => {
    const sizeItems = items.value.filter(i => i.sizeId === size.id)
    return {
      size,
      total: sizeItems.length,
      assigned: sizeItems.filter(i => i.assignedTo && !i.lostAt && !isLentOut(i.id)).length,
      free: sizeItems.filter(i => !i.assignedTo && !i.lostAt && !isLentOut(i.id)).length,
      lost: sizeItems.filter(i => i.lostAt).length,
      lent: sizeItems.filter(i => isLentOut(i.id)).length,
    }
  })
})

const noSizeItems = computed(() => {
  if (!detail.value?.hasSizes) return []
  const nosizeItems = items.value.filter(i => !i.sizeId)
  if (nosizeItems.length === 0) return []
  return [{
    size: null as InventorySize | null,
    total: nosizeItems.length,
    assigned: nosizeItems.filter(i => i.assignedTo && !i.lostAt && !isLentOut(i.id)).length,
    free: nosizeItems.filter(i => !i.assignedTo && !i.lostAt && !isLentOut(i.id)).length,
    lost: nosizeItems.filter(i => i.lostAt).length,
    lent: nosizeItems.filter(i => isLentOut(i.id)).length,
  }]
})

const allSizeStats = computed(() => [...sizeDistribution.value, ...noSizeItems.value])

const {loading, error, reload: loadData} = useAsyncLoader(async () => {
  const [inv, allItems, members, allContainers] = await Promise.all([
    inventory.getInventory(inventoryId.value),
    inventory.listItems(inventoryId.value),
    currentStationId.value ? stationMembers.listMembers() : Promise.resolve([]),
    inventoryContainers.listContainers().catch(() => [] as InventoryContainer[]),
  ])
  detail.value = inv
  items.value = allItems
  containers.value = allContainers
  const map = new Map<number, StationMember>()
  for (const m of members) map.set(m.id, m)
  memberMap.value = map
  try {
    const allProc = await procurement.listOpen()
    openProcurement.value = allProc.filter(p => p.inventoryId === inventoryId.value)
  } catch { openProcurement.value = [] }
  try {
    lentOutItems.value = await getLentOutByInventory(inventoryId.value)
  } catch { lentOutItems.value = [] }
})

function goBack() { router.push({ name: 'inventory-manage' }) }
</script>

<template>
  <ViewContent
      :title="t('pages.inventory-detail.title')"
      :subtitle="t('pages.inventory-detail.subtitle')"
  >
    <div class="space-y-6">
      <DetailHeader
        :name="detail?.name ?? ''"
        :inventory-type="detail?.inventoryType ?? null"
        :has-sizes="detail?.hasSizes ?? false"
        @back="goBack"
      />

      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <DetailLoadedContent
        v-if="!loading && detail"
        :detail="detail"
        :items="items"
        :free-items="freeItems"
        :lost-items="lostItems"
        :member-map="memberMap"
        :open-procurement="openProcurement"
        :lent-out-items="lentOutItems"
        :lent-item-station-map="lentItemStationMap"
        :container-path-by-id="containerPathById"
        :counts="counts"
        :all-size-stats="allSizeStats"
        :permissions="permissions"
        @fulfill-procurement="modals?.fulfillProcurement($event)"
        @open-procurement-modal="modals?.openProcurement()"
        @open-quick-assign="modals?.openQuickAssign()"
        @open-add="modals?.openAdd()"
        @assign="modals?.openAssign($event.id)"
        @unassign="modals?.unassign($event)"
        @edit="modals?.openEdit($event)"
        @mark-lost="modals?.markLost($event)"
        @mark-found="modals?.markFound($event)"
        @history="modals?.openHistory($event)"
        @delete="modals?.openDelete($event)"
        @assign-free="modals?.openAssign($event)"
      />

      <DetailViewModals
        ref="modals"
        :detail="detail"
        :member-map="memberMap"
        :unassigned-members="unassignedMembers"
        @reload="loadData"
        @error="error = $event"
      />
    </div>
  </ViewContent>
</template>
