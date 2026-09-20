/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useInventoryRoutes} from '@/composables/useInventoryRoutes'
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Alert from '@/components/feedback/Alert.vue'
import ExportFormatModal from '@/components/documents/ExportFormatModal.vue'
import AsyncSection from '@/components/feedback/AsyncSection.vue'
import MemberListHeader from './memberlistview/MemberListHeader.vue'
import MemberListBody from './memberlistview/MemberListBody.vue'
import MemberListFilters from './memberlistview/MemberListFilters.vue'
import SearchInput from '@/components/input/text/SearchInput.vue'
import { inventory, stationMembers, memberGroups, userTags } from '@/api'
import type { Inventory, InventoryItem } from '@/api/inventory'
import type { MemberGroup, StationMember, UserTag } from '@/api/types'
import { useMemberFilter } from '@/composables/useMemberFilter'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import { useDataTable } from '@/composables/useDataTable'
import { useInventoryMemberExport } from './memberlistview/useInventoryMemberExport'
import { inventoryIdOfColumn, inventoryMemberColumns, NAME_KEY } from './memberlistview/inventoryMemberColumns'
import { memberDisplayName } from '@/views/stationview/members/listview/useMemberData'
import { itemLabel, type ItemLabelParts } from './memberlistview/itemLabel'
import { getItem, setItem } from '@/api/storage'

const routes = useInventoryRoutes()

const { t } = useI18n()
const router = useRouter()

const members = ref<StationMember[]>([])
const inventories = ref<Inventory[]>([])
const allItems = ref<InventoryItem[]>([])
const sizeMap = ref<Map<number, string>>(new Map())
const groups = ref<MemberGroup[]>([])
const tags = ref<UserTag[]>([])
const memberGroupNames = ref<Map<number, string[]>>(new Map())
const memberTagNames = ref<Map<number, string[]>>(new Map())
const showEmpty = ref(false)

const showName = ref(getItem('inv-members-show-name') !== 'false')
const showInternalId = ref(getItem('inv-members-show-internal-id') === 'true')
const showSize = ref(getItem('inv-members-show-size') !== 'false')

const memberItemMap = computed(() => {
  const map = new Map<number, Map<number, InventoryItem[]>>()
  for (const item of allItems.value) {
    if (!item.assignedTo) continue
    if (!map.has(item.assignedTo)) map.set(item.assignedTo, new Map())
    const memberMap = map.get(item.assignedTo)!
    if (!memberMap.has(item.inventoryId)) memberMap.set(item.inventoryId, [])
    memberMap.get(item.inventoryId)!.push(item)
  }
  return map
})

const {
  onFilter,
  applyFilter: applyMemberFilter,
} = useMemberFilter(
    () => members.value,
    () => memberGroupNames.value,
    () => memberTagNames.value,
    () => groups.value,
    () => tags.value,
)

const candidates = computed(() => {
  const result = applyMemberFilter(members.value)
  return showEmpty.value ? result : result.filter(m => memberItemMap.value.has(m.id))
})

const parts = computed<ItemLabelParts>(() => ({
  showName: showName.value,
  showInternalId: showInternalId.value,
  showSize: showSize.value,
  sizeMap: sizeMap.value,
}))

function memberInventoryItems(memberId: number, inventoryId: number): InventoryItem[] {
  return memberItemMap.value.get(memberId)?.get(inventoryId) ?? []
}

function formatItemLabel(item: InventoryItem): string {
  return itemLabel(item, parts.value)
}

const table = useDataTable<StationMember>({
  id: 'inventory-members',
  rows: candidates,
  columns: computed(() => inventoryMemberColumns({
    t,
    inventories: inventories.value,
    itemsFor: memberInventoryItems,
    label: formatItemLabel,
  })),
  rowKey: member => member.id,
  sort: {key: NAME_KEY},
  searchText: member => member.email ?? '',
})

const visibleInventoryIds = computed(() => new Set(table.visibleColumns
  .map(column => inventoryIdOfColumn(column.key))
  .filter((id): id is number => id !== null)))

const displayedInventories = computed(() => inventories.value.filter(inv => visibleInventoryIds.value.has(inv.id)))

const {loading, error} = useAsyncLoader(async () => {
  const [mems, invs, grps, tgs] = await Promise.all([
    stationMembers.listMembers(),
    inventory.listInventories(),
    memberGroups.listGroups(),
    userTags.listTags(),
  ])
  members.value = mems
  inventories.value = invs
  groups.value = grps
  tags.value = tgs

  const [allItemsRes, allSizesRes, groupDetails, tagDetails] = await Promise.all([
    inventory.listAllItems(),
    inventory.listAllSizes(),
    Promise.all(grps.map(g => memberGroups.getGroupMembers(g.id))),
    Promise.all(tgs.map(tg => userTags.getTagMembers(tg.id))),
  ])
  allItems.value = allItemsRes

  const sm = new Map<number, string>()
  for (const s of allSizesRes) sm.set(s.id, s.label ?? '')
  sizeMap.value = sm

  const gNames = new Map<number, string[]>()
  grps.forEach((g, i) => {
    for (const m of groupDetails[i] ?? []) {
      if (!gNames.has(m.id)) gNames.set(m.id, [])
      gNames.get(m.id)!.push(g.name ?? '')
    }
  })
  memberGroupNames.value = gNames

  const tNames = new Map<number, string[]>()
  tgs.forEach((tg, i) => {
    for (const m of tagDetails[i] ?? []) {
      if (!tNames.has(m.id)) tNames.set(m.id, [])
      tNames.get(m.id)!.push(tg.name)
    }
  })
  memberTagNames.value = tNames
})

watch(showName, v => setItem('inv-members-show-name', String(v)))
watch(showInternalId, v => setItem('inv-members-show-internal-id', String(v)))
watch(showSize, v => setItem('inv-members-show-size', String(v)))

watch(() => table.rows, list => {
  if (!exportMode.value) return
  if (selectedForExport.value.size === 0) return
  const visibleIds = new Set(list.map(m => m.id))
  let changed = false
  const next = new Set<number>()
  for (const id of selectedForExport.value) {
    if (visibleIds.has(id)) next.add(id)
    else changed = true
  }
  if (changed) selectedForExport.value = next
})

const {
  exportMode,
  selectedMemberIds: selectedForExport,
  selectedFieldIds: selectedExportFields,
  allFields,
  exporting,
  exportError,
  enter: enterExportMode,
  cancel: cancelExport,
  toggleField: toggleExportField,
  toggleMember: toggleExportSelection,
  toggleSelectAll,
  runExport,
} = useInventoryMemberExport(
  () => table.rows,
  displayedInventories,
  visibleInventoryIds,
  {showName, showInternalId, showSize},
  memberDisplayName,
  memberInventoryItems,
  formatItemLabel,
)

const showExportFormat = ref(false)

function goToMember(memberId: number) {
  router.push({ name: routes.member, params: { memberId } })
}
</script>

<template>
  <ViewContent
      :title="t('pages.inventory-members.title')"
      :subtitle="t('pages.inventory-members.subtitle')"
  >
    <div class="space-y-6">
      <MemberListHeader
        :export-mode="exportMode"
        :exporting="exporting"
        :selected-count="selectedForExport.size"
        :has-members="table.rows.length > 0"
        @enter-export="enterExportMode"
        @cancel-export="cancelExport"
        @export="showExportFormat = true"
      />
      <ExportFormatModal
          v-model="showExportFormat"
          :formats="['csv', 'pdf']"
          :exporting="exporting"
          @export="runExport"
      />

      <Alert v-if="error || exportError" variant="error">{{ error || exportError }}</Alert>

      <AsyncSection :loading="loading">
        <SearchInput v-model="table.search" :placeholder="t('membersList.filter')" autofocus />

        <MemberListFilters
          v-model:show-empty="showEmpty"
          v-model:show-name="showName"
          v-model:show-internal-id="showInternalId"
          v-model:show-size="showSize"
          :groups="groups"
          :tags="tags"
          :table="table"
          @filter="onFilter"
        />

        <MemberListBody
          :table="table"
          :parts="parts"
          :export-mode="exportMode"
          :all-fields="allFields"
          :selected-export-fields="selectedExportFields"
          :selected-for-export="selectedForExport"
          :items-for="memberInventoryItems"
          @toggle-export-field="toggleExportField"
          @go-to-member="goToMember"
          @toggle-export-selection="toggleExportSelection"
          @toggle-select-all="toggleSelectAll"
        />
      </AsyncSection>
    </div>
  </ViewContent>
</template>
