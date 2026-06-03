/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import MultiSelectDropdown from '@/components/input/select/MultiSelectDropdown.vue'
import type {PermissionGrant, MemberGroup, UserTag} from '@/api/types'
import {StationUserType} from '@/api/types'

const {t} = useI18n()

const props = withDefaults(defineProps<{
  roles?: PermissionGrant[]
  groups?: MemberGroup[]
  tags?: UserTag[]
  selectedRoleIds?: number[]
  selectedUserTypes?: string[]
  selectedGroupIds: number[]
  selectedTagIds: number[]
  mode?: 'AND' | 'OR'
  showRoles?: boolean
  showGroups?: boolean
  showTags?: boolean
  showMode?: boolean
}>(), {
  selectedRoleIds: () => [],
  selectedUserTypes: () => [],
  showRoles: true,
  showGroups: true,
  showTags: true,
  showMode: true,
  mode: 'AND',
})

const emit = defineEmits<{
  'update:selectedRoleIds': [ids: number[]]
  'update:selectedUserTypes': [types: string[]]
  'update:selectedGroupIds': [ids: number[]]
  'update:selectedTagIds': [ids: number[]]
  'update:mode': [mode: 'AND' | 'OR']
}>()

const USER_TYPES = [StationUserType.MEMBER, StationUserType.TEAM, StationUserType.MANAGER] as readonly string[]

const roleFriendlyNames: Record<string, string> = {
  MEMBER: 'Alle',
  TEAM: 'Team',
  MANAGER: 'Verwaltung',
}

const roleOptions = computed(() =>
    (props.roles ?? [])
        .filter(r => USER_TYPES.includes(r.permission))
        .map(r => ({value: String(r.id), label: roleFriendlyNames[r.permission] ?? r.permission}))
)

const groupOptions = computed(() =>
    (props.groups ?? []).map(g => ({value: String(g.id), label: g.name ?? ''}))
)

const tagOptions = computed(() =>
    (props.tags ?? []).map(t => ({value: String(t.id), label: t.name}))
)

const selectedRoleValues = computed(() => {
  if (props.selectedUserTypes.length > 0) {
    // User type mode: find role IDs for the selected user type names
    return (props.roles ?? [])
        .filter(r => props.selectedUserTypes.includes(r.permission))
        .map(r => String(r.id))
  }
  return props.selectedRoleIds.map(String)
})
const selectedGroupValues = computed(() => props.selectedGroupIds.map(String))
const selectedTagValues = computed(() => props.selectedTagIds.map(String))

function onRolesChange(values: string[]) {
  // Always emit both for compatibility — callers listen to only one
  const ids = values.map(Number)
  const userTypes = (props.roles ?? [])
      .filter(r => ids.includes(r.id))
      .map(r => r.permission)
  emit('update:selectedRoleIds', ids)
  emit('update:selectedUserTypes', userTypes)
}

function onGroupsChange(values: string[]) {
  emit('update:selectedGroupIds', values.map(Number))
}

function onTagsChange(values: string[]) {
  emit('update:selectedTagIds', values.map(Number))
}

const internalMode = ref<'AND' | 'OR'>(props.mode ?? 'AND')

function toggleMode() {
  internalMode.value = internalMode.value === 'AND' ? 'OR' : 'AND'
  emit('update:mode', internalMode.value)
}

const hasActiveSelection = computed(() =>
    props.selectedRoleIds.length > 0 || props.selectedUserTypes.length > 0 || props.selectedGroupIds.length > 0 || props.selectedTagIds.length > 0
)

function reset() {
  emit('update:selectedRoleIds', [])
  emit('update:selectedUserTypes', [])
  emit('update:selectedGroupIds', [])
  emit('update:selectedTagIds', [])
}
</script>

<template>
  <div class="flex flex-wrap items-center gap-3">
    <!-- AND/OR toggle -->
    <SelectionToggleButton
        v-if="showMode"
        :selected="internalMode === 'AND'"
        size="sm"
        @toggle="toggleMode"
    >
      {{ internalMode === 'AND' ? t('restriction.and') : t('restriction.or') }}
    </SelectionToggleButton>

    <!-- Roles dropdown -->
    <MultiSelectDropdown
        v-if="showRoles && roleOptions.length > 0"
        :options="roleOptions"
        :model-value="selectedRoleValues"
        :placeholder="t('restriction.roles')"
        @update:model-value="onRolesChange"
    />

    <!-- Groups dropdown -->
    <MultiSelectDropdown
        v-if="showGroups && groupOptions.length > 0"
        :options="groupOptions"
        :model-value="selectedGroupValues"
        :placeholder="t('restriction.groups')"
        @update:model-value="onGroupsChange"
    />

    <!-- Tags dropdown -->
    <MultiSelectDropdown
        v-if="showTags && tagOptions.length > 0"
        :options="tagOptions"
        :model-value="selectedTagValues"
        :placeholder="t('restriction.tags')"
        @update:model-value="onTagsChange"
    />

    <!-- Reset button -->
    <SecondaryButton
        v-if="hasActiveSelection"
        @click="reset"
    >
      <font-awesome-icon :icon="['fas', 'xmark']" class="mr-1"/>
      {{ t('restriction.reset') }}
    </SecondaryButton>

    <!-- Empty hint -->
    <span v-if="!hasActiveSelection" class="text-xs text-(--text-muted) italic">
      {{ t('restriction.noRestrictions') }}
    </span>
  </div>
</template>
