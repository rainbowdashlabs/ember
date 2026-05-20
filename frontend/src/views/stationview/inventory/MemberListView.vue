/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import UserAvatar from '@/components/avatar/UserAvatar.vue'
import {inventory, stationMembers, memberGroups, profileFields} from '@/api'
import type {Inventory, InventoryItem, MemberGroup, ProfileField, Role, StationMember} from '@/api/types'
import {Roles} from '@/api/types'
import {useStations} from '@/composables/useStations'
import client from '@/api/client'

const {t} = useI18n()
const router = useRouter()
const {currentStationId} = useStations()

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
const filterRole = ref<string>('')
const showEmpty = ref(false)
const visibleInventoryIds = ref<Set<number>>(new Set())

// Display options
const showName = ref(true)
const showInternalId = ref(false)
const showSize = ref(true)

// Export
const exportMode = ref(false)
const selectedForExport = ref<Set<number>>(new Set())
const selectedExportFields = ref<Set<number>>(new Set())
const allFields = ref<ProfileField[]>([])
const exporting = ref(false)

// Role translations
const roleLabels: Record<string, string> = {
  MEMBER: 'Mitglied',
  GUARDIAN: 'Erziehungsberechtigter',
  TEAM: 'Team',
}

function translateRole(role: string): string {
  return roleLabels[role] ?? role
}

// Member → items map (includes lost items)
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

// Only show these roles in the filter dropdown
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

const filteredMembers = computed(() => {
  let result = members.value

  // Filter by role
  if (filterRole.value) {
    result = result.filter(m => memberRoleMap.value.get(m.id)?.has(filterRole.value) ?? false)
  }

  // Filter by groups (multi-select: member must be in ALL selected groups)
  if (filterGroups.value.size > 0) {
    result = result.filter(m => {
      for (const groupId of filterGroups.value) {
        const memberIds = groupMemberMap.value.get(groupId) ?? new Set()
        if (!memberIds.has(m.id)) return false
      }
      return true
    })
  }

  // Filter out members without inventory
  if (!showEmpty.value) {
    result = result.filter(m => memberItemMap.value.has(m.id))
  }

  return result.sort((a, b) => memberDisplayName(a).localeCompare(memberDisplayName(b)))
})

const displayedInventories = computed(() => {
  return inventories.value.filter(inv => visibleInventoryIds.value.has(inv.id))
})

function memberDisplayName(m: StationMember): string {
  return m.name && m.name.trim() ? m.name : m.email ?? `#${m.id}`
}

function memberInventoryItems(memberId: number, inventoryId: number): InventoryItem[] {
  return memberItemMap.value.get(memberId)?.get(inventoryId) ?? []
}

function memberInventoryCount(memberId: number, inventoryId: number): number {
  return memberInventoryItems(memberId, inventoryId).length
}

function formatItemLabel(item: InventoryItem): string {
  const parts: string[] = []
  if (showName.value && item.name) parts.push(item.name)
  if (showInternalId.value && item.internalId) parts.push(`(${item.internalId})`)
  if (showSize.value && item.sizeId) {
    const label = sizeMap.value.get(item.sizeId)
    if (label) parts.push(label)
  }
  return parts.join(' ') || item.name || '–'
}

function itemNamePart(item: InventoryItem): string {
  const parts: string[] = []
  if (showName.value && item.name) parts.push(item.name)
  if (showInternalId.value && item.internalId) parts.push(`(${item.internalId})`)
  return parts.join(' ')
}

function itemSizeLabel(item: InventoryItem): string {
  if (!showSize.value) return ''
  if (!item.sizeId) return t('common.unisize')
  return sizeMap.value.get(item.sizeId) ?? t('common.unisize')
}

function toggleInventory(invId: number) {
  const s = new Set(visibleInventoryIds.value)
  if (s.has(invId)) s.delete(invId)
  else s.add(invId)
  visibleInventoryIds.value = s
}

function toggleGroupFilter(groupId: number) {
  const s = new Set(filterGroups.value)
  if (s.has(groupId)) s.delete(groupId)
  else s.add(groupId)
  filterGroups.value = s
}

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const stationId = currentStationId.value!
    const [mems, invs, grps, roles] = await Promise.all([
      stationMembers.listMembers(stationId),
      inventory.listInventories(),
      memberGroups.listGroups(),
      stationMembers.listAllRoles(),
    ])
    members.value = mems
    inventories.value = invs
    groups.value = grps
    allRoles.value = roles

    // Show all inventories by default
    visibleInventoryIds.value = new Set(invs.map(i => i.id))

    // Load all items and sizes for all inventories in parallel
    const [itemArrays, sizeArrays] = await Promise.all([
      Promise.all(invs.map(inv => inventory.listItems(inv.id))),
      Promise.all(invs.map(inv => inventory.listSizes(inv.id))),
    ])
    allItems.value = itemArrays.flat()

    // Build global size lookup
    const sm = new Map<number, string>()
    for (const sizes of sizeArrays) {
      for (const s of sizes) sm.set(s.id, s.label ?? '')
    }
    sizeMap.value = sm

    // Load group membership
    const gMap = new Map<number, Set<number>>()
    const groupDetails = await Promise.all(grps.map(g => memberGroups.getGroupMembers(g.id)))
    grps.forEach((g, i) => {
      gMap.set(g.id, new Set(groupDetails[i].map(m => m.id)))
    })
    groupMemberMap.value = gMap

    // Load roles for all members
    const roleMap = new Map<number, Set<string>>()
    const roleResults = await Promise.all(mems.map(m => stationMembers.getRoles(m.id).catch(() => [])))
    mems.forEach((m, i) => {
      roleMap.set(m.id, new Set(roleResults[i].map(r => r.role)))
    })
    memberRoleMap.value = roleMap
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

// Export functions
async function enterExportMode() {
  exportMode.value = true
  selectedForExport.value = new Set(filteredMembers.value.map(m => m.id))
  selectedExportFields.value = new Set()
  try {
    allFields.value = await profileFields.listFields()
  } catch { allFields.value = [] }
}

function cancelExport() {
  exportMode.value = false
  selectedForExport.value = new Set()
  selectedExportFields.value = new Set()
}

function toggleExportField(fieldId: number) {
  const s = new Set(selectedExportFields.value)
  if (s.has(fieldId)) s.delete(fieldId)
  else s.add(fieldId)
  selectedExportFields.value = s
}

function toggleExportSelection(id: number) {
  const s = new Set(selectedForExport.value)
  if (s.has(id)) s.delete(id)
  else s.add(id)
  selectedForExport.value = s
}

function toggleSelectAll() {
  if (selectedForExport.value.size === filteredMembers.value.length) {
    selectedForExport.value = new Set()
  } else {
    selectedForExport.value = new Set(filteredMembers.value.map(m => m.id))
  }
}

function exportCsv() {
  const selected = filteredMembers.value.filter(m => selectedForExport.value.has(m.id))
  const headers = [t('membersList.colName')]
  for (const inv of displayedInventories.value) {
    headers.push(inv.name ?? '')
  }

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
  const blob = new Blob(['\uFEFF' + csv], {type: 'text/csv;charset=utf-8'})
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
      showName: showName.value,
      showInternalId: showInternalId.value,
      showSize: showSize.value,
    }, {responseType: 'blob'})
    const blob = res.data as Blob
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'inventory-members.pdf'
    a.click()
    URL.revokeObjectURL(url)
    exportMode.value = false
  } catch {
    error.value = t('common.error')
  } finally {
    exporting.value = false
  }
}

function goToMember(memberId: number) {
  router.push({name: 'inventory-member', params: {memberId}})
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
            <SecondaryButton @click="cancelExport">
              {{ t('common.cancel') }}
            </SecondaryButton>
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
        <!-- Filters -->
        <NeutralContainer class="flex flex-wrap items-center gap-4">
          <div class="flex items-center gap-2">
            <label class="text-sm font-medium">{{ t('inventoryMembers.role') }}</label>
            <SelectInput v-model="filterRole" class="w-40 text-sm">
              <option value="">{{ t('inventoryMembers.allRoles') }}</option>
              <option v-for="role in filterableRoles" :key="role" :value="role">{{ translateRole(role) }}</option>
            </SelectInput>
          </div>
          <div class="flex items-center gap-2">
            <label class="text-sm font-medium">{{ t('inventoryMembers.showEmpty') }}</label>
            <ToggleInput v-model="showEmpty" />
          </div>
        </NeutralContainer>

        <!-- Group multi-select -->
        <NeutralContainer v-if="groups.length > 0" class="space-y-2">
          <p class="text-sm font-medium">{{ t('inventoryMembers.group') }}</p>
          <div class="flex flex-wrap gap-2">
            <label v-for="g in groups" :key="g.id" class="inline-flex items-center gap-1.5 text-sm cursor-pointer">
              <input type="checkbox" :checked="filterGroups.has(g.id)" @change="toggleGroupFilter(g.id)" />
              {{ g.name }}
            </label>
          </div>
        </NeutralContainer>

        <!-- Inventory column toggles -->
        <NeutralContainer class="space-y-2">
          <p class="text-sm font-medium">{{ t('inventoryMembers.columns') }}</p>
          <div class="flex flex-wrap gap-2">
            <label v-for="inv in inventories" :key="inv.id" class="inline-flex items-center gap-1.5 text-sm cursor-pointer">
              <input type="checkbox" :checked="visibleInventoryIds.has(inv.id)" @change="toggleInventory(inv.id)" />
              {{ inv.name }}
            </label>
          </div>
        </NeutralContainer>

        <!-- Display options -->
        <NeutralContainer class="space-y-2">
          <p class="text-sm font-medium">{{ t('inventoryMembers.displayOptions') }}</p>
          <div class="flex flex-wrap gap-4">
            <label class="inline-flex items-center gap-1.5 text-sm cursor-pointer">
              <input type="checkbox" v-model="showName" />
              {{ t('inventoryMembers.optName') }}
            </label>
            <label class="inline-flex items-center gap-1.5 text-sm cursor-pointer">
              <input type="checkbox" v-model="showInternalId" />
              {{ t('inventoryMembers.optInternalId') }}
            </label>
            <label class="inline-flex items-center gap-1.5 text-sm cursor-pointer">
              <input type="checkbox" v-model="showSize" />
              {{ t('inventoryMembers.optSize') }}
            </label>
          </div>
        </NeutralContainer>

        <!-- Export field picker -->
        <NeutralContainer v-if="exportMode && allFields.length > 0" class="space-y-2">
          <p class="text-sm font-medium">{{ t('inventoryMembers.exportFieldsHint') }}</p>
          <div class="flex flex-wrap gap-2">
            <label v-for="field in allFields" :key="field.id" class="inline-flex items-center gap-1.5 text-sm cursor-pointer">
              <input type="checkbox" :checked="selectedExportFields.has(field.id)" @change="toggleExportField(field.id)" />
              {{ field.name }}
            </label>
          </div>
        </NeutralContainer>

        <div v-if="filteredMembers.length === 0" class="text-center text-(--text-muted) py-8">
          {{ t('inventoryMembers.empty') }}
        </div>

        <!-- Member table -->
        <NeutralContainer v-if="filteredMembers.length > 0" class="overflow-x-auto">
          <table class="w-full text-sm">
            <thead>
              <tr class="border-b border-bg-light-accent dark:border-bg-dark-accent text-left">
                <th v-if="exportMode" class="px-1 py-2 w-8">
                  <input type="checkbox" :checked="selectedForExport.size === filteredMembers.length && filteredMembers.length > 0" @change="toggleSelectAll" />
                </th>
                <th class="px-3 py-2 font-medium">{{ t('membersList.colName') }}</th>
                <th v-for="inv in displayedInventories" :key="inv.id" class="px-3 py-2 font-medium">{{ inv.name }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="member in filteredMembers" :key="member.id"
                  class="border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50 hover:bg-(--bg-accent)/30 cursor-pointer"
                  @click="exportMode ? toggleExportSelection(member.id) : goToMember(member.id)">
                <td v-if="exportMode" class="px-1 py-2.5 w-8" @click.stop>
                  <input type="checkbox" :checked="selectedForExport.has(member.id)" @change="toggleExportSelection(member.id)" />
                </td>
                <td class="px-3 py-2.5 font-medium text-primary">
                  <div class="flex items-center gap-2">
                    <UserAvatar :member-id="member.id" :name="memberDisplayName(member)" size="sm"/>
                    {{ memberDisplayName(member) }}
                  </div>
                </td>
                <td v-for="inv in displayedInventories" :key="inv.id" class="px-3 py-2.5">
                  <template v-if="memberInventoryCount(member.id, inv.id) > 0">
                    <div class="flex flex-wrap gap-1">
                      <span v-for="item in memberInventoryItems(member.id, inv.id)" :key="item.id"
                            :class="item.lostAt ? 'text-error' : ''"
                            class="inline-flex items-center gap-1 text-xs">
                        <template v-if="itemNamePart(item)">{{ itemNamePart(item) }}</template>
                        <span v-if="itemSizeLabel(item)" :class="item.lostAt ? 'bg-error/15 text-error' : 'bg-secondary/15 text-secondary-accent'" class="rounded-full px-1.5 py-0 text-[10px]">{{ itemSizeLabel(item) }}</span>
                        <span v-if="item.lostAt" class="text-[10px]">({{ t('inventoryMembers.lost') }})</span>
                      </span>
                    </div>
                  </template>
                  <span v-else class="text-(--text-muted)">—</span>
                </td>
              </tr>
            </tbody>
          </table>
        </NeutralContainer>

        <p v-if="filteredMembers.length > 0" class="text-xs text-(--text-muted)">
          {{ filteredMembers.length }} {{ t('inventoryMembers.memberCount') }}
        </p>
      </template>
    </div>
  </ViewContent>
</template>
