/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import ToggleSwitch from '@/components/input/toggle/ToggleSwitch.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import MultiSelectDropdown from '@/components/input/select/MultiSelectDropdown.vue'
import type {MemberGroup, StationMember, UserTag} from '@/api/types'
import {StationUserType, StationUserTypeLabels} from '@/api/types'
import type {RestrictionSelection} from '@/components/input/restriction'

const {t} = useI18n()

const props = withDefaults(defineProps<{
  groups?: MemberGroup[]
  tags?: UserTag[]
  members?: StationMember[]
  showUserTypes?: boolean
  showGroups?: boolean
  showTags?: boolean
  showMembers?: boolean
  showMode?: boolean
}>(), {
  showUserTypes: true,
  showGroups: true,
  showTags: true,
  showMembers: false,
  showMode: true,
})

const model = defineModel<RestrictionSelection>({required: true})

function patch(part: Partial<RestrictionSelection>) {
  model.value = {...model.value, ...part}
}

const mode = computed({
  get: () => model.value.mode,
  set: (v: 'AND' | 'OR') => patch({mode: v}),
})

const userTypeOptions = computed(() =>
    Object.values(StationUserType).map(ut => ({value: ut, label: StationUserTypeLabels[ut] ?? ut}))
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

const selectedGroupValues = computed(() => model.value.groupIds.map(String))
const selectedTagValues = computed(() => model.value.tagIds.map(String))
const selectedMemberValues = computed(() => model.value.memberIds.map(String))

function onUserTypesChange(values: string[]) {
  patch({userTypes: values})
}

function onGroupsChange(values: string[]) {
  patch({groupIds: values.map(Number)})
}

function onTagsChange(values: string[]) {
  patch({tagIds: values.map(Number)})
}

function onMembersChange(values: string[]) {
  patch({memberIds: values.map(Number)})
}

const hasActiveSelection = computed(() =>
    model.value.userTypes.length > 0 || model.value.groupIds.length > 0
    || model.value.tagIds.length > 0 || model.value.memberIds.length > 0
)

function reset() {
  patch({userTypes: [], groupIds: [], tagIds: [], memberIds: []})
}
</script>

<template>
  <div class="flex flex-wrap items-center gap-3">
    <ToggleSwitch
        v-if="showMode && (showGroups || showTags)"
        v-model="mode"
        option-a="AND"
        option-b="OR"
        :label-a="t('restriction.and')"
        :label-b="t('restriction.or')"
    />

    <MultiSelectDropdown
        v-if="showUserTypes"
        :options="userTypeOptions"
        :model-value="model.userTypes"
        :placeholder="t('restriction.userTypes')"
        @update:model-value="onUserTypesChange"
    />

    <MultiSelectDropdown
        v-if="showGroups && groupOptions.length > 0"
        :options="groupOptions"
        :model-value="selectedGroupValues"
        :placeholder="t('restriction.groups')"
        @update:model-value="onGroupsChange"
    />

    <MultiSelectDropdown
        v-if="showTags && tagOptions.length > 0"
        :options="tagOptions"
        :model-value="selectedTagValues"
        :placeholder="t('restriction.tags')"
        @update:model-value="onTagsChange"
    />

    <MultiSelectDropdown
        v-if="showMembers && memberOptions.length > 0"
        :options="memberOptions"
        :model-value="selectedMemberValues"
        :placeholder="t('restriction.members')"
        @update:model-value="onMembersChange"
    />

    <ErrorButton
        v-if="hasActiveSelection"
        @click="reset"
    >
      <font-awesome-icon :icon="['fas', 'xmark']" class="mr-1"/>
      {{ t('restriction.reset') }}
    </ErrorButton>

    <span v-if="!hasActiveSelection" class="text-xs text-(--text-muted) italic">
      {{ t('restriction.noRestrictions') }}
    </span>
  </div>
</template>
