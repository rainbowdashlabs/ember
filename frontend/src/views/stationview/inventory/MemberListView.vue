/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import MemberListHeader from './memberlistview/MemberListHeader.vue'
import MemberListBody from './memberlistview/MemberListBody.vue'
import SearchInput from '@/components/input/text/SearchInput.vue'
import { inventory, stationMembers, memberGroups, profileFields, userTags } from '@/api'
import type { Inventory, InventoryItem, MemberGroup, ProfileField, StationMember, UserTag } from '@/api/types'
import { useMemberFilter } from '@/composables/useMemberFilter'
import { useBreakpoint } from '@/composables/useBreakpoint'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import client from '@/api/client'
import { getItem, setItem } from '@/api/storage'

const { t } = useI18n()
const router = useRouter()
const { isMobile } = useBreakpoint()

const members = ref<StationMember[]>([])
const inventories = ref<Inventory[]>([])
const allItems = ref<InventoryItem[]>([])
const sizeMap = ref<Map<number, string>>(new Map())
const groups = ref<MemberGroup[]>([])
const tags = ref<UserTag[]>([])
const memberGroupNames = ref<Map<number, string[]>>(new Map())
const memberTagNames = ref<Map<number, string[]>>(new Map())
const showEmpty = ref(false)
const visibleInventoryIds = ref<Set<number>>(new Set())

const showName = ref(getItem('inv-members-show-name') !== 'false')
const showInternalId = ref(getItem('inv-members-show-internal-id') === 'true')
const showSize = ref(getItem('inv-members-show-size') !== 'false')

const exportMode = ref(false)
const selectedForExport = ref<Set<number>>(new Set())
const selectedExportFields = ref<Set<number>>(new Set())
const allFields = ref<ProfileField[]>([])
const exporting = ref(false)
const filterText = ref('')

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

function memberDisplayName(m: StationMember): string {
  return m.name && m.name.trim() ? m.name : m.email ?? `#${m.id}`
}

const filteredMembers = computed(() => {
  let result = applyMemberFilter(members.value)
  if (!showEmpty.value) {
    result = result.filter(m => memberItemMap.value.has(m.id))
  }
  const q = filterText.value.toLowerCase().trim()
  if (q) {
    result = result.filter(m =>
        memberDisplayName(m).toLowerCase().includes(q)
        || (m.email ?? '').toLowerCase().includes(q))
  }
  return result.sort((a, b) => memberDisplayName(a).localeCompare(memberDisplayName(b)))
})

const displayedInventories = computed(() => inventories.value.filter(inv => visibleInventoryIds.value.has(inv.id)))

function toggleInventory(invId: number) {
  const s = new Set(visibleInventoryIds.value)
  if (s.has(invId)) s.delete(invId); else s.add(invId)
  visibleInventoryIds.value = s
}

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

  const storedIds = getItem('inv-members-visible-ids')
  if (storedIds) {
    try {
      const parsed = JSON.parse(storedIds) as number[]
      const validIds = new Set(invs.map(i => i.id))
      visibleInventoryIds.value = new Set(parsed.filter(id => validIds.has(id)))
    } catch { visibleInventoryIds.value = new Set(invs.map(i => i.id)) }
  } else {
    visibleInventoryIds.value = new Set(invs.map(i => i.id))
  }

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
    for (const m of groupDetails[i]) {
      if (!gNames.has(m.id)) gNames.set(m.id, [])
      gNames.get(m.id)!.push(g.name ?? '')
    }
  })
  memberGroupNames.value = gNames

  const tNames = new Map<number, string[]>()
  tgs.forEach((tg, i) => {
    for (const m of tagDetails[i]) {
      if (!tNames.has(m.id)) tNames.set(m.id, [])
      tNames.get(m.id)!.push(tg.name)
    }
  })
  memberTagNames.value = tNames
})

watch(visibleInventoryIds, ids => setItem('inv-members-visible-ids', JSON.stringify([...ids])))
watch(showName, v => setItem('inv-members-show-name', String(v)))
watch(showInternalId, v => setItem('inv-members-show-internal-id', String(v)))
watch(showSize, v => setItem('inv-members-show-size', String(v)))

watch(filteredMembers, list => {
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

async function enterExportMode() {
  exportMode.value = true
  selectedForExport.value = new Set(filteredMembers.value.map(m => m.id))
  selectedExportFields.value = new Set()
  try { allFields.value = await profileFields.listFields() } catch { allFields.value = [] }
}

function cancelExport() {
  exportMode.value = false
  selectedForExport.value = new Set()
  selectedExportFields.value = new Set()
}

function toggleExportField(fieldId: number) {
  const s = new Set(selectedExportFields.value)
  if (s.has(fieldId)) s.delete(fieldId); else s.add(fieldId)
  selectedExportFields.value = s
}

function toggleExportSelection(id: number) {
  const s = new Set(selectedForExport.value)
  if (s.has(id)) s.delete(id); else s.add(id)
  selectedForExport.value = s
}

function toggleSelectAll() {
  if (selectedForExport.value.size === filteredMembers.value.length) {
    selectedForExport.value = new Set()
  } else {
    selectedForExport.value = new Set(filteredMembers.value.map(m => m.id))
  }
}

function formatItemLabel(item: InventoryItem): string {
  const parts: string[] = []
  if (showName.value && item.name) parts.push(item.name)
  if (showInternalId.value && item.internalId) parts.push(`(${item.internalId})`)
  if (showSize.value && item.sizeId) {
    const label = sizeMap.value.get(item.sizeId)
    if (label) parts.push(label)
  }
  return parts.join(' ') || item.name || '-'
}

function memberInventoryItems(memberId: number, inventoryId: number): InventoryItem[] {
  return memberItemMap.value.get(memberId)?.get(inventoryId) ?? []
}

function exportCsv() {
  const selected = filteredMembers.value.filter(m => selectedForExport.value.has(m.id))
  const headers = [t('membersList.colName')]
  for (const inv of displayedInventories.value) headers.push(inv.name ?? '')

  const rows: string[][] = []
  for (const member of selected) {
    const row = [memberDisplayName(member)]
    for (const inv of displayedInventories.value) {
      const items = memberInventoryItems(member.id, inv.id)
      row.push(items.map(i => {
        const label = formatItemLabel(i)
        return i.lostAt ? `${label} (${t('inventoryMembers.lost')})` : label
      }).join(', '))
    }
    rows.push(row)
  }

  const csv = [headers.join(';'), ...rows.map(r => r.map(c => `"${c.replace(/"/g, '""')}"`).join(';'))].join('\n')
  const blob = new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = 'inventory-members.csv'
  a.click()
  URL.revokeObjectURL(url)
  exportMode.value = false
}

async function exportPdf() {
  exporting.value = true
  try {
    const memberIds = [...selectedForExport.value]
    const inventoryIds = [...visibleInventoryIds.value]
    const extraFieldIds = [...selectedExportFields.value]
    const res = await client.post('/inventories/members/export', {
      memberIds, inventoryIds, extraFieldIds,
      showName: showName.value, showInternalId: showInternalId.value, showSize: showSize.value,
    }, { responseType: 'blob' })
    const blob = res.data as Blob
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'inventory-members.pdf'
    a.click()
    URL.revokeObjectURL(url)
    exportMode.value = false
  } catch { error.value = t('common.error') }
  finally { exporting.value = false }
}

function goToMember(memberId: number) {
  router.push({ name: 'inventory-member', params: { memberId } })
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
        :has-members="filteredMembers.length > 0"
        @enter-export="enterExportMode"
        @cancel-export="cancelExport"
        @export-csv="exportCsv"
        @export-pdf="exportPdf"
      />

      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <SearchInput v-if="!loading" v-model="filterText" :placeholder="t('membersList.filter')" autofocus />

      <MemberListBody
        v-if="!loading"
        v-model:show-empty="showEmpty"
        :groups="groups"
        :tags="tags"
        :inventories="inventories"
        :displayed-inventories="displayedInventories"
        :visible-inventory-ids="visibleInventoryIds"
        :show-name="showName"
        :show-internal-id="showInternalId"
        :show-size="showSize"
        :export-mode="exportMode"
        :all-fields="allFields"
        :selected-export-fields="selectedExportFields"
        :filtered-members="filteredMembers"
        :selected-for-export="selectedForExport"
        :is-mobile="isMobile"
        :member-item-map="memberItemMap"
        :size-map="sizeMap"
        @update:show-name="showName = $event"
        @update:show-internal-id="showInternalId = $event"
        @update:show-size="showSize = $event"
        @filter="onFilter"
        @toggle-inventory="toggleInventory"
        @toggle-export-field="toggleExportField"
        @go-to-member="goToMember"
        @toggle-export-selection="toggleExportSelection"
        @toggle-select-all="toggleSelectAll"
      />
    </div>
  </ViewContent>
</template>
