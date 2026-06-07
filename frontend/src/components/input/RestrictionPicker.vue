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
import type {MemberGroup, StationMember, UserTag} from '@/api/types'
import {StationUserType} from '@/api/types'

const {t} = useI18n()

const props = withDefaults(defineProps<{
  groups?: MemberGroup[]
  tags?: UserTag[]
  members?: StationMember[]
  selectedUserTypes?: string[]
  selectedGroupIds: number[]
  selectedTagIds: number[]
  selectedMemberIds?: number[]
  mode?: 'AND' | 'OR'
  showUserTypes?: boolean
  showGroups?: boolean
  showTags?: boolean
  showMembers?: boolean
  showMode?: boolean
}>(), {
  selectedUserTypes: () => [],
  selectedMemberIds: () => [],
  showUserTypes: true,
  showGroups: true,
  showTags: true,
  showMembers: false,
  showMode: true,
  mode: 'AND',
})

const emit = defineEmits<{
  'update:selectedUserTypes': [types: string[]]
  'update:selectedGroupIds': [ids: number[]]
  'update:selectedTagIds': [ids: number[]]
  'update:selectedMemberIds': [ids: number[]]
  'update:mode': [mode: 'AND' | 'OR']
}>()

const USER_TYPE_OPTIONS = [
  {value: StationUserType.TRIAL, label: 'Probe'},
  {value: StationUserType.MEMBER, label: 'Mitglied'},
  {value: StationUserType.GUARDIAN, label: 'Erziehungsberechtigter'},
  {value: StationUserType.TEAM, label: 'Team'},
  {value: StationUserType.MANAGER, label: 'Manager'},
]

const userTypeOptions = computed(() =>
    USER_TYPE_OPTIONS.map(o => ({value: o.value, label: o.label}))
)

const groupOptions = computed(() =>
    (props.groups ?? []).map(g => ({value: String(g.id), label: g.name ?? ''}))
)

const tagOptions = computed(() =>
    (props.tags ?? []).map(t => ({value: String(t.id), label: t.name}))
)

const memberOptions = computed(() =>
    (props.members ?? []).map(m => ({value: String(m.id), label: m.name ?? m.email ?? `#${m.id}`}))
)

const selectedUserTypeValues = computed(() => props.selectedUserTypes)
const selectedGroupValues = computed(() => props.selectedGroupIds.map(String))
const selectedTagValues = computed(() => props.selectedTagIds.map(String))
const selectedMemberValues = computed(() => props.selectedMemberIds.map(String))

function onUserTypesChange(values: string[]) {
  emit('update:selectedUserTypes', values)
}

function onGroupsChange(values: string[]) {
  emit('update:selectedGroupIds', values.map(Number))
}

function onTagsChange(values: string[]) {
  emit('update:selectedTagIds', values.map(Number))
}

function onMembersChange(values: string[]) {
  emit('update:selectedMemberIds', values.map(Number))
}

const internalMode = ref<'AND' | 'OR'>(props.mode ?? 'AND')

function toggleMode() {
  internalMode.value = internalMode.value === 'AND' ? 'OR' : 'AND'
  emit('update:mode', internalMode.value)
}

const hasActiveSelection = computed(() =>
    props.selectedUserTypes.length > 0 || props.selectedGroupIds.length > 0 || props.selectedTagIds.length > 0 || props.selectedMemberIds.length > 0
)

function reset() {
  emit('update:selectedUserTypes', [])
  emit('update:selectedGroupIds', [])
  emit('update:selectedTagIds', [])
  emit('update:selectedMemberIds', [])
}
</script>

<template>
  <div class="flex flex-wrap items-center gap-3">
    <!-- AND/OR toggle (only between groups and tags — user types are always OR) -->
    <SelectionToggleButton
        v-if="showMode && (showGroups || showTags)"
        :selected="internalMode === 'AND'"
        size="sm"
        @toggle="toggleMode"
    >
      {{ internalMode === 'AND' ? t('restriction.and') : t('restriction.or') }}
    </SelectionToggleButton>

    <!-- User types dropdown (always OR-connected) -->
    <MultiSelectDropdown
        v-if="showUserTypes"
        :options="userTypeOptions"
        :model-value="selectedUserTypeValues"
        :placeholder="t('restriction.userTypes')"
        @update:model-value="onUserTypesChange"
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

    <!-- Members dropdown -->
    <MultiSelectDropdown
        v-if="showMembers && memberOptions.length > 0"
        :options="memberOptions"
        :model-value="selectedMemberValues"
        :placeholder="t('restriction.members')"
        @update:model-value="onMembersChange"
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
