/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MemberListFilters from './memberlistview/MemberListFilters.vue'
import MemberListTable from './memberlistview/MemberListTable.vue'
import { inventory, stationMembers, memberGroups, profileFields } from '@/api'
import type { Inventory, InventoryItem, MemberGroup, ProfileField, Role, StationMember } from '@/api/types'
import { Roles } from '@/api/types'
import { useStations } from '@/composables/useStations'
import { useBreakpoint } from '@/composables/useBreakpoint'
import client from '@/api/client'
import { getItem, setItem } from '@/api/storage'

const { t } = useI18n()
const router = useRouter()
const { currentStationId } = useStations()
const { isMobile } = useBreakpoint()

const members = ref<StationMember[]>([])
const inventories = ref<Inventory[]>([])
const allItems = ref<InventoryItem[]>([])
const sizeMap = ref<Map<number, string>>(new Map())
const groups = ref<MemberGroup[]>([])
const groupMemberMap = ref<Map<number, Set<number>>>(new Map())
const allRoles = ref<Role[]>([])
const memberRoleMap = ref<Map<number, Set<string>>>(new Map())
const loading = ref(true)
const error = ref('')

// Filters
const filterGroups = ref<Set<number>>(new Set())
const filterRole = ref('')
const showEmpty = ref(false)
const visibleInventoryIds = ref<Set<number>>(new Set())

// Display options
const showName = ref(getItem('inv-members-show-name') !== 'false')
const showInternalId = ref(getItem('inv-members-show-internal-id') === 'true')
const showSize = ref(getItem('inv-members-show-size') !== 'false')

// Export
const exportMode = ref(false)
const selectedForExport = ref<Set<number>>(new Set())
const selectedExportFields = ref<Set<number>>(new Set())
const allFields = ref<ProfileField[]>([])
const exporting = ref(false)

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

const allowedFilterRoles: readonly string[] = [Roles.MEMBER, Roles.GUARDIAN, Roles.TEAM]

const filterableRoles = computed(() => {
  const roleNames = new Set<string>()
  for (const roles of memberRoleMap.value.values()) {
    for (const r of roles) {
      if (allowedFilterRoles.includes(r)) roleNames.add(r)
    }
  }
  return [...roleNames].sort()
})

function memberDisplayName(m: StationMember): string {
  return m.name && m.name.trim() ? m.name : m.email ?? `#${m.id}`
}

const filteredMembers = computed(() => {
  let result = members.value
  if (filterRole.value) {
    result = result.filter(m => memberRoleMap.value.get(m.id)?.has(filterRole.value) ?? false)
  }
  if (filterGroups.value.size > 0) {
    result = result.filter(m => {
      for (const groupId of filterGroups.value) {
        if (!(groupMemberMap.value.get(groupId) ?? new Set()).has(m.id)) return false
      }
      return true
    })
  }
  if (!showEmpty.value) {
    result = result.filter(m => memberItemMap.value.has(m.id))
  }
  return result.sort((a, b) => memberDisplayName(a).localeCompare(memberDisplayName(b)))
})

const displayedInventories = computed(() => inventories.value.filter(inv => visibleInventoryIds.value.has(inv.id)))

function toggleInventory(invId: number) {
  const s = new Set(visibleInventoryIds.value)
  if (s.has(invId)) s.delete(invId); else s.add(invId)
  visibleInventoryIds.value = s
}

function toggleGroupFilter(groupId: number) {
  const s = new Set(filterGroups.value)
  if (s.has(groupId)) s.delete(groupId); else s.add(groupId)
  filterGroups.value = s
}

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const stationId = currentStationId.value!
    const [mems, invs, grps, roles] = await Promise.all([
      stationMembers.listMembers(),
      inventory.listInventories(),
      memberGroups.listGroups(),
      stationMembers.listAllRoles(),
    ])
    members.value = mems
    inventories.value = invs
    groups.value = grps
    allRoles.value = roles

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

    const [allItemsRes, allSizesRes, groupDetails, allMemberRoles] = await Promise.all([
      inventory.listAllItems(),
      inventory.listAllSizes(),
      Promise.all(grps.map(g => memberGroups.getGroupMembers(g.id))),
      stationMembers.getAllMemberRoles(),
    ])
    allItems.value = allItemsRes

    const sm = new Map<number, string>()
    for (const s of allSizesRes) sm.set(s.id, s.label ?? '')
    sizeMap.value = sm

    const gMap = new Map<number, Set<number>>()
    grps.forEach((g, i) => { gMap.set(g.id, new Set(groupDetails[i].map(m => m.id))) })
    groupMemberMap.value = gMap

    const roleMap = new Map<number, Set<string>>()
    for (const [memberId, memberRoles] of Object.entries(allMemberRoles)) {
      roleMap.set(Number(memberId), new Set(memberRoles.map(r => r.role)))
    }
    memberRoleMap.value = roleMap
  } catch { error.value = t('common.error') }
  finally { loading.value = false }
}

watch(visibleInventoryIds, ids => setItem('inv-members-visible-ids', JSON.stringify([...ids])))
watch(showName, v => setItem('inv-members-show-name', String(v)))
watch(showInternalId, v => setItem('inv-members-show-internal-id', String(v)))
watch(showSize, v => setItem('inv-members-show-size', String(v)))

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

onMounted(loadData)
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <div class="flex items-center justify-between flex-wrap gap-2">
        <SectionHeader>{{ t('inventoryMembers.title') }}</SectionHeader>
        <div class="flex items-center gap-2">
          <template v-if="exportMode">
            <SecondaryButton :disabled="exporting || selectedForExport.size === 0" @click="exportCsv">
              <font-awesome-icon :icon="['fas', 'download']" class="mr-1" />
              CSV ({{ selectedForExport.size }})
            </SecondaryButton>
            <SecondaryButton :disabled="exporting || selectedForExport.size === 0" @click="exportPdf">
              <font-awesome-icon :icon="['fas', 'download']" class="mr-1" />
              {{ exporting ? t('common.loading') : 'PDF' }} ({{ selectedForExport.size }})
            </SecondaryButton>
            <SecondaryButton @click="cancelExport">{{ t('common.cancel') }}</SecondaryButton>
          </template>
          <template v-else>
            <PrimaryButton v-if="filteredMembers.length > 0" @click="enterExportMode">
              <font-awesome-icon :icon="['fas', 'file-export']" class="mr-1" />
              {{ t('inventoryMembers.export') }}
            </PrimaryButton>
          </template>
        </div>
      </div>

      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading">
        <MemberListFilters
          v-model:filter-role="filterRole"
          v-model:show-empty="showEmpty"
          :filterable-roles="filterableRoles"
          :groups="groups"
          :filter-groups="filterGroups"
          :inventories="inventories"
          :visible-inventory-ids="visibleInventoryIds"
          :show-name="showName"
          :show-internal-id="showInternalId"
          :show-size="showSize"
          @toggle-group-filter="toggleGroupFilter"
          @toggle-inventory="toggleInventory"
          @update:show-name="showName = $event"
          @update:show-internal-id="showInternalId = $event"
          @update:show-size="showSize = $event"
        />

        <!-- Export field picker -->
        <NeutralContainer v-if="exportMode && allFields.length > 0" class="space-y-2">
          <p class="text-sm font-medium">{{ t('inventoryMembers.exportFieldsHint') }}</p>
          <div class="flex flex-wrap gap-2">
            <label v-for="field in allFields" :key="field.id" class="inline-flex items-center gap-1.5 text-sm cursor-pointer">
              <CheckboxInput :model-value="selectedExportFields.has(field.id)" @update:model-value="toggleExportField(field.id)" />
              {{ field.name }}
            </label>
          </div>
        </NeutralContainer>

        <div v-if="filteredMembers.length === 0" class="text-center text-(--text-muted) py-8">
          {{ t('inventoryMembers.empty') }}
        </div>

        <MemberListTable
          v-if="filteredMembers.length > 0"
          :members="filteredMembers"
          :inventories="displayedInventories"
          :export-mode="exportMode"
          :selected-for-export="selectedForExport"
          :is-mobile="isMobile"
          :member-item-map="memberItemMap"
          :show-name="showName"
          :show-internal-id="showInternalId"
          :show-size="showSize"
          :size-map="sizeMap"
          @go-to-member="goToMember"
          @toggle-export-selection="toggleExportSelection"
          @toggle-select-all="toggleSelectAll"
        />

        <p v-if="filteredMembers.length > 0" class="text-xs text-(--text-muted)">
          {{ filteredMembers.length }} {{ t('inventoryMembers.memberCount') }}
        </p>
      </template>
    </div>
  </ViewContent>
</template>
