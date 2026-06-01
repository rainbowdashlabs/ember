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
import type {Role, MemberGroup, UserTag} from '@/api/types'
import {Roles} from '@/api/types'

const {t} = useI18n()

const props = withDefaults(defineProps<{
  roles?: Role[]
  groups?: MemberGroup[]
  tags?: UserTag[]
  selectedRoleIds: number[]
  selectedGroupIds: number[]
  selectedTagIds: number[]
  mode?: 'AND' | 'OR'
  showRoles?: boolean
  showGroups?: boolean
  showTags?: boolean
  showMode?: boolean
}>(), {
  showRoles: true,
  showGroups: true,
  showTags: true,
  showMode: true,
  mode: 'AND',
})

const emit = defineEmits<{
  'update:selectedRoleIds': [ids: number[]]
  'update:selectedGroupIds': [ids: number[]]
  'update:selectedTagIds': [ids: number[]]
  'update:mode': [mode: 'AND' | 'OR']
}>()

const USER_ROLES = [Roles.USER, Roles.TEAM, Roles.MANAGER] as readonly string[]

const roleFriendlyNames: Record<string, string> = {
  USER: 'Alle',
  TEAM: 'Team',
  MANAGER: 'Verwaltung',
}

const roleOptions = computed(() =>
    (props.roles ?? [])
        .filter(r => USER_ROLES.includes(r.role))
        .map(r => ({value: String(r.id), label: roleFriendlyNames[r.role] ?? r.role}))
)

const groupOptions = computed(() =>
    (props.groups ?? []).map(g => ({value: String(g.id), label: g.name ?? ''}))
)

const tagOptions = computed(() =>
    (props.tags ?? []).map(t => ({value: String(t.id), label: t.name}))
)

const selectedRoleValues = computed(() => props.selectedRoleIds.map(String))
const selectedGroupValues = computed(() => props.selectedGroupIds.map(String))
const selectedTagValues = computed(() => props.selectedTagIds.map(String))

function onRolesChange(values: string[]) {
  emit('update:selectedRoleIds', values.map(Number))
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
    props.selectedRoleIds.length > 0 || props.selectedGroupIds.length > 0 || props.selectedTagIds.length > 0
)

function reset() {
  emit('update:selectedRoleIds', [])
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
