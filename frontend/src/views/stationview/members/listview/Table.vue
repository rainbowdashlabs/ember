/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import EditButton from '@/components/button/EditButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import type {ProfileField, StationMember} from '@/api/types'
import {Roles, hasTeamRole} from '@/api/types'

const {t} = useI18n()

const props = defineProps<{
  members: StationMember[]
  visibleColumns: ProfileField[]
  expandedId: number | null
  sortColumn: 'name' | number
  sortAsc: boolean
  columnFilters: Map<'name' | 'groups' | number, string>
  columnMultiFilters: Map<'groups' | number, Set<string>>
  memberGroupsMap: Map<number, string[]>
  memberRolesMap: Map<number, string[]>
  memberManagers: Map<number, StationMember[]>
  allMembers: StationMember[]
  overviewFields: ProfileField[]
  getFieldValue: (memberId: number, fieldId: number) => string
  exportMode?: boolean
  selectedIds?: Set<number>
}>()

const emit = defineEmits<{
  toggleSort: [column: 'name' | number]
  setColumnFilter: [column: 'name' | number, value: string]
  toggleMultiFilter: [key: 'groups' | number, value: string]
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

function sortIcon(column: 'name' | number): string {
  if (props.sortColumn !== column) return 'sort'
  return props.sortAsc ? 'sort-up' : 'sort-down'
}

function getColumnFilter(column: 'name' | number): string {
  return props.columnFilters.get(column) ?? ''
}

function getMultiFilter(key: 'groups' | number): Set<string> {
  return props.columnMultiFilters.get(key) ?? new Set()
}

function isMultiSelectField(field: ProfileField): boolean {
  return field.fieldType === 'enum' || field.fieldType === 'number' || field.fieldType === 'age'
}

function memberDisplayName(m: StationMember): string {
  return m.name && m.name.trim() ? m.name : m.email ?? `#${m.id}`
}

function getMemberGroups(memberId: number): string[] {
  return props.memberGroupsMap.get(memberId) ?? []
}

function getUniqueGroupValues(): string[] {
  const vals = new Set<string>()
  for (const m of props.members) {
    for (const g of getMemberGroups(m.id)) vals.add(g)
  }
  return [...vals].sort()
}

function getUniqueFieldValues(fieldId: number): string[] {
  const vals = new Set<string>()
  for (const m of props.members) {
    const v = props.getFieldValue(m.id, fieldId)
    if (v) vals.add(v)
  }
  return [...vals].sort()
}

function getScopesForRoles(roles: string[]): string[] {
  const scopes: string[] = []
  if (roles.includes(Roles.MEMBER)) scopes.push(Roles.MEMBER)
  if (hasTeamRole(roles)) scopes.push(Roles.TEAM)
  if (roles.includes(Roles.MEMBER_MANAGER)) scopes.push(Roles.MEMBER_MANAGER)
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
  [Roles.MEMBER_MANAGER]: 'Mitgliedsmanager',
  [Roles.MEMBER]: 'Mitglied',
}

function getPrimaryRole(memberId: number): string {
  const roles = props.memberRolesMap.get(memberId) ?? []
  if (hasTeamRole(roles)) return Roles.TEAM
  if (roles.includes(Roles.MEMBER_MANAGER)) return Roles.MEMBER_MANAGER
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
  <div class="overflow-x-auto">
    <table class="w-full text-sm">
      <thead>
      <tr class="border-b border-bg-light-accent dark:border-bg-dark-accent text-left">
        <th v-if="exportMode" class="px-2 py-2 w-10">
          <input :checked="allSelected" class="h-4 w-4 rounded accent-primary cursor-pointer" type="checkbox"
                 @change="emit('toggleSelectAll')"/>
        </th>
        <th class="px-3 py-2 font-medium cursor-pointer select-none hover:text-primary"
            @click="emit('toggleSort', 'name')">
          <span class="inline-flex items-center gap-1">{{ t('membersList.colName') }} <font-awesome-icon
              :icon="['fas', sortIcon('name')]" class="h-3 w-3 text-(--text-muted)"/></span>
        </th>
        <th class="px-3 py-2 font-medium">{{ t('membersList.colRole') }}</th>
        <th class="px-3 py-2 font-medium">{{ t('membersList.colEmail') }}</th>
        <th class="px-3 py-2 font-medium">{{ t('membersList.colGroups') }}</th>
        <th v-for="field in visibleColumns" :key="field.id"
            class="px-3 py-2 font-medium cursor-pointer select-none hover:text-primary"
            @click="emit('toggleSort', field.id)">
          <span class="inline-flex items-center gap-1">{{ field.name }} <font-awesome-icon
              :icon="['fas', sortIcon(field.id)]" class="h-3 w-3 text-(--text-muted)"/></span>
        </th>
        <th v-if="!exportMode" class="px-3 py-2 w-20"></th>
      </tr>
      <tr class="border-b border-bg-light-accent dark:border-bg-dark-accent">
        <td v-if="exportMode" class="px-2 py-1"></td>
        <td class="px-2 py-1">
          <input
              :placeholder="t('membersList.filterColumn')"
              :value="getColumnFilter('name')"
              class="w-full px-2 py-1 text-xs rounded border border-bg-light-accent dark:border-bg-dark-accent bg-transparent focus:border-primary focus:outline-none"
              type="text"
              @input="emit('setColumnFilter', 'name', ($event.target as HTMLInputElement).value)"
          />
        </td>
        <td class="px-2 py-1"></td>
        <td class="px-2 py-1"></td>
        <td class="px-2 py-1">
          <div class="flex flex-wrap gap-1">
            <button
                v-for="val in getUniqueGroupValues()"
                :key="val"
                :class="getMultiFilter('groups').has(val) ? 'border-primary bg-primary/15 text-primary' : 'border-bg-light-accent dark:border-bg-dark-accent text-(--text-muted) hover:border-primary'"
                class="px-1.5 py-0.5 text-xs rounded border transition-colors"
                @click="emit('toggleMultiFilter', 'groups', val)"
            >
              {{ val }}
            </button>
          </div>
        </td>
        <td v-for="field in visibleColumns" :key="field.id" class="px-2 py-1">
          <div v-if="isMultiSelectField(field)" class="flex flex-wrap gap-1">
            <button
                v-for="val in getUniqueFieldValues(field.id)"
                :key="val"
                :class="getMultiFilter(field.id).has(val) ? 'border-primary bg-primary/15 text-primary' : 'border-bg-light-accent dark:border-bg-dark-accent text-(--text-muted) hover:border-primary'"
                class="px-1.5 py-0.5 text-xs rounded border transition-colors"
                @click="emit('toggleMultiFilter', field.id, val)"
            >
              {{ val }}
            </button>
          </div>
          <input
              v-else
              :placeholder="t('membersList.filterColumn')"
              :value="getColumnFilter(field.id)"
              class="w-full px-2 py-1 text-xs rounded border border-bg-light-accent dark:border-bg-dark-accent bg-transparent focus:border-primary focus:outline-none"
              type="text"
              @input="emit('setColumnFilter', field.id, ($event.target as HTMLInputElement).value)"
          />
        </td>
        <td v-if="!exportMode" class="px-2 py-1"></td>
      </tr>
      </thead>
      <tbody>
      <template v-for="member in members" :key="member.id">
        <tr
            :class="{
              'bg-bg-light-accent/30 dark:bg-bg-dark-accent/30': !exportMode && expandedId === member.id,
              'bg-primary/5': exportMode && selectedIds?.has(member.id),
            }"
            class="border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50 cursor-pointer hover:bg-bg-light-accent/30 dark:hover:bg-bg-dark-accent/30 transition-colors"
            @click="onRowClick(member)"
        >
          <td v-if="exportMode" class="px-2 py-2.5" @click.stop>
            <input :checked="selectedIds?.has(member.id)" class="h-4 w-4 rounded accent-primary cursor-pointer"
                   type="checkbox" @change="emit('toggleSelect', member.id)"/>
          </td>
          <td class="px-3 py-2.5">
            <span class="font-medium">{{ memberDisplayName(member) }}</span>
            <ErrorBadge v-if="member.profileComplete === false" class="ml-1.5 text-[10px]">{{ t('membersList.incomplete') }}</ErrorBadge>
          </td>
          <td class="px-3 py-2.5">
            <span
                v-if="getPrimaryRole(member.id)"
                class="inline-block rounded-full px-2 py-0.5 text-[10px] font-medium"
                :class="{
                  'bg-primary/10 text-primary': getPrimaryRole(member.id) === 'TEAM',
                  'bg-info/10 text-info-accent': getPrimaryRole(member.id) === 'MEMBER_MANAGER',
                  'bg-bg-light-accent dark:bg-bg-dark-accent text-(--text-muted)': getPrimaryRole(member.id) === 'MEMBER',
                }"
            >{{ primaryRoleLabels[getPrimaryRole(member.id)] }}</span>
          </td>
          <td class="px-3 py-2.5 text-(--text-muted) text-xs">
            {{ member.email || '–' }}
          </td>
          <td class="px-3 py-2.5 text-(--text-muted) text-xs">
            {{ getMemberGroups(member.id).join(', ') || '–' }}
          </td>
          <td
              v-for="field in visibleColumns"
              :key="field.id"
              :class="isFieldApplicable(member.id, field) ? 'text-(--text-muted)' : 'bg-bg-light-accent/40 dark:bg-bg-dark-accent/40'"
              class="px-3 py-2.5"
          >
            <template v-if="isFieldApplicable(member.id, field)">{{
                getFieldValue(member.id, field.id) || '–'
              }}
            </template>
          </td>
          <td v-if="!exportMode" class="px-3 py-2.5 text-right">
            <IconButton :icon="['fas', 'eye']" :label="t('membersList.detail')" class="text-primary hover:bg-primary/15"
                        @click="emit('navigateDetail', member, $event)"/>
            <EditButton @click="emit('navigateEdit', member, $event)"/>
          </td>
        </tr>
        <tr v-if="!exportMode && expandedId === member.id">
          <td :colspan="visibleColumns.length + 5" class="px-3 py-4 bg-bg-light-accent/20 dark:bg-bg-dark-accent/20">
            <div class="space-y-3">
              <div class="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
                <div v-for="field in getApplicableOverviewFields(member.id)" :key="field.id" class="text-sm">
                  <span class="text-(--text-muted)">{{ field.name }}:</span>
                  <span class="ml-1 font-medium">{{ getFieldValue(member.id, field.id) || '–' }}</span>
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
                      <font-awesome-icon :icon="['fas', 'user']" class="text-(--text-muted) h-3 w-3"/>
                      <span class="text-sm font-medium">{{ managerName(mgr) }}</span>
                      <span v-if="mgr.email" class="text-xs text-(--text-muted)">{{ mgr.email }}</span>
                    </div>
                    <div class="grid gap-x-4 gap-y-1 sm:grid-cols-2 lg:grid-cols-3 pl-5">
                      <div v-for="field in getApplicableOverviewFields(mgr.id)" :key="field.id" class="text-xs">
                        <span class="text-(--text-muted)">{{ field.name }}:</span>
                        <span class="ml-1">{{ getFieldValue(mgr.id, field.id) || '–' }}</span>
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

  <div v-if="members.length === 0" class="text-center text-(--text-muted) py-8">{{ t('membersList.empty') }}</div>
</template>
