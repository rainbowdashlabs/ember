/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import EditButton from '@/components/button/EditButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import ColumnFilterModal from './ColumnFilterModal.vue'
import type {ProfileField, StationMember} from '@/api/types'
import {Roles, hasTeamRole} from '@/api/types'
import FieldValueDisplay from '@/components/display/FieldValueDisplay.vue'
import {useBreakpoint} from '@/composables/useBreakpoint'
import EmptyState from '@/components/feedback/EmptyState.vue'
import Th from '@/components/table/Th.vue'
import Td from '@/components/table/Td.vue'
import THead from '@/components/table/THead.vue'
import TRow from '@/components/table/TRow.vue'
import MutedIcon from '@/components/display/MutedIcon.vue'

const {isMobile} = useBreakpoint()

const {t} = useI18n()

const props = defineProps<{
  members: StationMember[]
  visibleColumns: ProfileField[]
  expandedId: number | null
  sortColumn: 'name' | number
  sortAsc: boolean
  columnMultiFilters: Map<'name' | 'groups' | 'tags' | number, Set<string>>
  columnEmptyFilters: Set<'name' | 'groups' | 'tags' | number>
  memberGroupsMap: Map<number, string[]>
  memberTagsMap: Map<number, string[]>
  memberRolesMap: Map<number, string[]>
  memberManagers: Map<number, StationMember[]>
  allMembers: StationMember[]
  overviewFields: ProfileField[]
  getFieldValue: (memberId: number, fieldId: number) => unknown
  exportMode?: boolean
  selectedIds?: Set<number>
}>()

const emit = defineEmits<{
  toggleSort: [column: 'name' | number]
  applyColumnFilter: [key: 'name' | 'groups' | 'tags' | number, selected: Set<string>, includeEmpty: boolean]
  toggleExpand: [member: StationMember]
  navigateDetail: [member: StationMember, event: Event]
  navigateEdit: [member: StationMember, event: Event]
  toggleSelect: [memberId: number]
  toggleSelectAll: []
}>()

const allSelected = computed(() => {
  if (!props.selectedIds || props.members.length === 0) return false
  return props.members.every(m => props.selectedIds!.has(m.id))
})

// Filter modal state
const filterModalOpen = ref(false)
const filterModalColumn = ref<'name' | 'groups' | 'tags' | number>('name')
const filterModalLabel = ref('')
const filterModalValues = ref<string[]>([])
const filterModalSelected = ref<Set<string>>(new Set())
const filterModalIncludeEmpty = ref(false)

function sortIcon(column: 'name' | number): string {
  if (props.sortColumn !== column) return 'sort'
  return props.sortAsc ? 'sort-up' : 'sort-down'
}

function hasActiveFilter(key: 'name' | 'groups' | 'tags' | number): boolean {
  const multi = props.columnMultiFilters.get(key)
  if (multi && multi.size > 0) return true
  return props.columnEmptyFilters.has(key)
}

function openFilterModal(key: 'name' | 'groups' | 'tags' | number, label: string) {
  filterModalColumn.value = key
  filterModalLabel.value = label
  filterModalValues.value = getUniqueValuesForColumn(key)
  filterModalSelected.value = new Set(props.columnMultiFilters.get(key) ?? [])
  filterModalIncludeEmpty.value = props.columnEmptyFilters.has(key)
  filterModalOpen.value = true
}

function getUniqueValuesForColumn(key: 'name' | 'groups' | 'tags' | number): string[] {
  const vals = new Set<string>()
  for (const m of props.allMembers) {
    if (key === 'name') {
      const name = memberDisplayName(m)
      if (name && name !== `#${m.id}`) vals.add(name)
    } else if (key === 'groups') {
      for (const g of getMemberGroups(m.id)) vals.add(g)
    } else if (key === 'tags') {
      for (const tag of getMemberTags(m.id)) vals.add(tag)
    } else {
      const v = props.getFieldValue(m.id, key)
      if (v != null && v !== '') vals.add(String(v))
    }
  }
  return [...vals].sort()
}

function onFilterApply(selected: Set<string>, includeEmpty: boolean) {
  emit('applyColumnFilter', filterModalColumn.value, selected, includeEmpty)
}

function memberDisplayName(m: StationMember): string {
  return m.name && m.name.trim() ? m.name : m.email ?? `#${m.id}`
}

function getMemberGroups(memberId: number): string[] {
  return props.memberGroupsMap.get(memberId) ?? []
}

function getMemberTags(memberId: number): string[] {
  return props.memberTagsMap.get(memberId) ?? []
}

function getScopesForRoles(roles: string[]): string[] {
  const scopes: string[] = []
  if (roles.includes(Roles.MEMBER)) scopes.push(Roles.MEMBER)
  if (hasTeamRole(roles)) scopes.push(Roles.TEAM)
  if (roles.includes(Roles.GUARDIAN)) scopes.push(Roles.GUARDIAN)
  return scopes
}

function isFieldApplicable(memberId: number, field: ProfileField): boolean {
  const roles = props.memberRolesMap.get(memberId) ?? []
  return getScopesForRoles(roles).includes(field.scope ?? Roles.MEMBER)
}

function getApplicableOverviewFields(memberId: number): ProfileField[] {
  const roles = props.memberRolesMap.get(memberId) ?? []
  const scopes = getScopesForRoles(roles)
  return props.overviewFields.filter(f => {
    if (f.scope === 'GROUP') return false
    return scopes.includes(f.scope ?? Roles.MEMBER)
  })
}

function getManagers(memberId: number): StationMember[] {
  return props.memberManagers.get(memberId) ?? []
}

function managerName(mgr: StationMember): string {
  const m = props.allMembers.find(mem => mem.id === mgr.id)
  return m ? memberDisplayName(m) : `#${mgr.id}`
}

const primaryRoleLabels: Record<string, string> = {
  [Roles.TEAM]: 'Team',
  [Roles.GUARDIAN]: 'Erziehungsberechtigter',
  [Roles.MEMBER]: 'Mitglied',
}

function getPrimaryRole(memberId: number): string {
  const roles = props.memberRolesMap.get(memberId) ?? []
  if (hasTeamRole(roles)) return Roles.TEAM
  if (roles.includes(Roles.GUARDIAN)) return Roles.GUARDIAN
  if (roles.includes(Roles.MEMBER)) return Roles.MEMBER
  return ''
}

function onRowClick(member: StationMember) {
  if (props.exportMode) {
    emit('toggleSelect', member.id)
  } else {
    emit('toggleExpand', member)
  }
}
</script>

<template>
  <!-- Mobile card layout -->
  <div v-if="isMobile" class="space-y-3">
    <EmptyState v-if="members.length === 0">{{ t('membersList.empty') }}</EmptyState>
    <NeutralContainer v-for="member in members" :key="member.id" class="space-y-2" @click="onRowClick(member)">
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-2">
          <div>
            <MemberName :identity="member.identity" :name="memberDisplayName(member)" size="sm" class="font-medium"/>
            <ErrorBadge v-if="member.profileComplete === false" class="ml-1.5 text-[10px]">{{ t('membersList.incomplete') }}</ErrorBadge>
            <div v-if="getPrimaryRole(member.id)" class="mt-0.5">
              <span
                  class="inline-block rounded-full px-2 py-0.5 text-[10px] font-medium"
                  :class="{
                    'bg-primary/10 text-primary': getPrimaryRole(member.id) === 'TEAM',
                    'bg-info/10 text-info-accent': getPrimaryRole(member.id) === 'GUARDIAN',
                    'bg-bg-light-accent dark:bg-bg-dark-accent text-(--text-muted)': getPrimaryRole(member.id) === 'MEMBER',
                  }"
              >{{ primaryRoleLabels[getPrimaryRole(member.id)] }}</span>
            </div>
          </div>
        </div>
        <div v-if="!exportMode" class="flex gap-1" @click.stop>
          <IconButton :icon="['fas', 'eye']" :label="t('membersList.detail')" class="text-primary hover:bg-primary/15" @click="emit('navigateDetail', member, $event)"/>
          <EditButton @click="emit('navigateEdit', member, $event)"/>
        </div>
        <div v-else @click.stop>
          <CheckboxInput :model-value="selectedIds?.has(member.id) ?? false" @update:model-value="emit('toggleSelect', member.id)"/>
        </div>
      </div>
      <div class="grid grid-cols-2 gap-x-4 gap-y-1 text-sm mt-1">
        <div v-if="member.email" class="col-span-2 text-(--text-muted) text-xs">{{ member.email }}</div>
        <div v-if="getMemberGroups(member.id).length > 0" class="text-xs">
          <div class="text-(--text-muted)">{{ t('membersList.colGroups') }}</div>
          <div>{{ getMemberGroups(member.id).join(', ') }}</div>
        </div>
        <div v-if="getMemberTags(member.id).length > 0" class="text-xs">
          <div class="text-(--text-muted)">{{ t('membersList.colTags') }}</div>
          <div>{{ getMemberTags(member.id).join(', ') }}</div>
        </div>
        <template v-for="field in visibleColumns" :key="field.id">
          <div v-if="isFieldApplicable(member.id, field) && getFieldValue(member.id, field.id)" class="text-xs">
            <div class="text-(--text-muted)">{{ field.name }}</div>
            <div><FieldValueDisplay :value="getFieldValue(member.id, field.id)" :field-type="field.fieldType"/></div>
          </div>
        </template>
      </div>
    </NeutralContainer>
  </div>

  <!-- Desktop table layout -->
  <template v-else>
  <div class="overflow-x-auto">
    <table class="w-full text-sm">
      <thead>
      <THead>
        <th v-if="exportMode" class="px-2 py-2 w-10">
          <CheckboxInput :model-value="allSelected" @update:model-value="emit('toggleSelectAll')"/>
        </th>
        <th v-if="!exportMode" class="px-3 py-2 w-20"></th>
        <!-- Name column -->
        <Th>
          <span class="inline-flex items-center gap-1">
            {{ t('membersList.colName') }}
            <font-awesome-icon
                :icon="['fas', sortIcon('name')]"
                class="h-3 w-3 text-(--text-muted) cursor-pointer hover:text-primary"
                @click="emit('toggleSort', 'name')"
            />
            <font-awesome-icon
                :icon="['fas', 'filter']"
                :class="hasActiveFilter('name') ? 'text-primary' : 'text-(--text-muted)'"
                class="h-3 w-3 cursor-pointer hover:text-primary"
                @click.stop="openFilterModal('name', t('membersList.colName'))"
            />
          </span>
        </Th>
        <!-- Role column -->
        <Th>{{ t('membersList.colRole') }}</Th>
        <!-- Email column -->
        <Th>{{ t('membersList.colEmail') }}</Th>
        <!-- Groups column -->
        <Th>
          <span class="inline-flex items-center gap-1">
            {{ t('membersList.colGroups') }}
            <font-awesome-icon
                :icon="['fas', 'filter']"
                :class="hasActiveFilter('groups') ? 'text-primary' : 'text-(--text-muted)'"
                class="h-3 w-3 cursor-pointer hover:text-primary"
                @click.stop="openFilterModal('groups', t('membersList.colGroups'))"
            />
          </span>
        </Th>
        <!-- Tags column -->
        <Th>
          <span class="inline-flex items-center gap-1">
            {{ t('membersList.colTags') }}
            <font-awesome-icon
                :icon="['fas', 'filter']"
                :class="hasActiveFilter('tags') ? 'text-primary' : 'text-(--text-muted)'"
                class="h-3 w-3 cursor-pointer hover:text-primary"
                @click.stop="openFilterModal('tags', t('membersList.colTags'))"
            />
          </span>
        </Th>
        <!-- Dynamic field columns -->
        <Th v-for="field in visibleColumns" :key="field.id">
          <span class="inline-flex items-center gap-1">
            {{ field.name }}
            <font-awesome-icon
                :icon="['fas', sortIcon(field.id)]"
                class="h-3 w-3 text-(--text-muted) cursor-pointer hover:text-primary"
                @click="emit('toggleSort', field.id)"
            />
            <font-awesome-icon
                :icon="['fas', 'filter']"
                :class="hasActiveFilter(field.id) ? 'text-primary' : 'text-(--text-muted)'"
                class="h-3 w-3 cursor-pointer hover:text-primary"
                @click.stop="openFilterModal(field.id, field.name ?? '')"
            />
          </span>
        </Th>
      </THead>
      </thead>
      <tbody>
      <template v-for="member in members" :key="member.id">
        <TRow
            :class="{
              'bg-bg-light-accent/30 dark:bg-bg-dark-accent/30': !exportMode && expandedId === member.id,
              'bg-primary/5': exportMode && selectedIds?.has(member.id),
              'cursor-pointer hover:bg-bg-light-accent/30 dark:hover:bg-bg-dark-accent/30 transition-colors': true,
            }"
            @click="onRowClick(member)"
        >
          <td v-if="exportMode" class="px-2 py-2.5" @click.stop>
            <CheckboxInput :model-value="selectedIds?.has(member.id) ?? false" @update:model-value="emit('toggleSelect', member.id)"/>
          </td>
          <Td v-if="!exportMode" @click.stop>
            <IconButton :icon="['fas', 'eye']" :label="t('membersList.detail')"
                        class="text-primary hover:bg-primary/15"
                        @click="emit('navigateDetail', member, $event)"/>
            <EditButton @click="emit('navigateEdit', member, $event)"/>
          </Td>
          <Td>
            <div class="flex items-center gap-2">
              <MemberName :identity="member.identity" :name="memberDisplayName(member)" size="sm" class="font-medium"/>
              <ErrorBadge v-if="member.profileComplete === false" class="ml-1.5 text-[10px]">{{
                  t('membersList.incomplete')
                }}</ErrorBadge>
            </div>
          </Td>
          <Td>
            <span
                v-if="getPrimaryRole(member.id)"
                class="inline-block rounded-full px-2 py-0.5 text-[10px] font-medium"
                :class="{
                  'bg-primary/10 text-primary': getPrimaryRole(member.id) === 'TEAM',
                  'bg-info/10 text-info-accent': getPrimaryRole(member.id) === 'GUARDIAN',
                  'bg-bg-light-accent dark:bg-bg-dark-accent text-(--text-muted)': getPrimaryRole(member.id) === 'MEMBER',
                }"
            >{{ primaryRoleLabels[getPrimaryRole(member.id)] }}</span>
          </Td>
          <Td class="text-(--text-muted) text-xs">
            {{ member.email || '–' }}
          </Td>
          <Td class="text-(--text-muted) text-xs">
            {{ getMemberGroups(member.id).join(', ') || '–' }}
          </Td>
          <Td class="text-(--text-muted) text-xs">
            {{ getMemberTags(member.id).join(', ') || '–' }}
          </Td>
          <Td
              v-for="field in visibleColumns"
              :key="field.id"
              :class="isFieldApplicable(member.id, field) ? 'text-(--text-muted)' : 'bg-bg-light-accent/40 dark:bg-bg-dark-accent/40'"
          >
            <template v-if="isFieldApplicable(member.id, field)">
              <FieldValueDisplay :value="getFieldValue(member.id, field.id)" :field-type="field.fieldType"/>
            </template>
          </Td>
        </TRow>
        <tr v-if="!exportMode && expandedId === member.id">
          <td :colspan="visibleColumns.length + 8" class="px-3 py-4 bg-bg-light-accent/20 dark:bg-bg-dark-accent/20">
            <div class="space-y-3">
              <div class="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
                <div v-for="field in getApplicableOverviewFields(member.id)" :key="field.id" class="text-sm">
                  <span class="text-(--text-muted)">{{ field.name }}:</span>
                  <span class="ml-1 font-medium"><FieldValueDisplay :value="getFieldValue(member.id, field.id)" :field-type="field.fieldType"/></span>
                </div>
              </div>
              <div v-if="getManagers(member.id).length > 0">
                <h4 class="text-xs font-semibold uppercase text-(--text-muted) mb-2">{{
                    t('membersList.managers')
                  }}</h4>
                <div class="space-y-2">
                  <div v-for="mgr in getManagers(member.id)" :key="mgr.id"
                       class="rounded-lg px-4 py-3 bg-bg-light-accent/40 dark:bg-bg-dark-accent/40 space-y-1">
                    <div class="flex items-center gap-2">
                      <MutedIcon :icon="['fas', 'user']"/>
                      <span class="text-sm font-medium">{{ managerName(mgr) }}</span>
                      <span v-if="mgr.email" class="text-xs text-(--text-muted)">{{ mgr.email }}</span>
                    </div>
                    <div class="grid gap-x-4 gap-y-1 sm:grid-cols-2 lg:grid-cols-3 pl-5">
                      <div v-for="field in getApplicableOverviewFields(mgr.id)" :key="field.id" class="text-xs">
                        <span class="text-(--text-muted)">{{ field.name }}:</span>
                        <span class="ml-1"><FieldValueDisplay :value="getFieldValue(mgr.id, field.id)" :field-type="field.fieldType"/></span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </td>
        </tr>
      </template>
      </tbody>
    </table>
  </div>

  <EmptyState v-if="members.length === 0">{{ t('membersList.empty') }}</EmptyState>
  </template>

  <ColumnFilterModal
      v-model="filterModalOpen"
      :column-label="filterModalLabel"
      :values="filterModalValues"
      :selected-values="filterModalSelected"
      :include-empty="filterModalIncludeEmpty"
      @apply="onFilterApply"
      @close="filterModalOpen = false"
  />
</template>
