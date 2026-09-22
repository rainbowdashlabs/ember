/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import MultiSelectDropdown from '@/components/input/select/MultiSelectDropdown.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import ToggleSwitch from '@/components/input/toggle/ToggleSwitch.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import ResultFieldConditions from './ResultFieldConditions.vue'
import {ResultMatch, type ResultFilter, type ResultMatchName} from '@/api/forms'
import type {ProfileField} from '@/api/profileFields'
import {StationUserType, StationUserTypeLabels, type MemberGroup, type UserTag} from '@/api/types'

/**
 * Which respondents count: by user type, groups, tags, age and profile answers.
 *
 * Groups and tags offer "one of them" and "all of them" once more than one is picked, so the
 * members who are in both group A and group B can be looked at on their own.
 */
const props = defineProps<{
  groups: MemberGroup[]
  tags: UserTag[]
  fields: ProfileField[]
}>()

const filter = defineModel<ResultFilter>({required: true})

const {t} = useI18n()

function patch(part: Partial<ResultFilter>) {
  filter.value = {...filter.value, ...part}
}

const userTypeOptions = Object.values(StationUserType).map(type => ({value: type, label: StationUserTypeLabels[type]}))
const groupOptions = computed(() => props.groups.map(group => ({value: String(group.id), label: group.name ?? ''})))
const tagOptions = computed(() => props.tags.map(tag => ({value: String(tag.id), label: tag.name})))

const userTypes = computed({
  get: () => filter.value.userTypes,
  set: (values: string[]) => patch({userTypes: values}),
})
const groupIds = computed({
  get: () => filter.value.groupIds.map(String),
  set: (values: string[]) => patch({groupIds: values.map(Number)}),
})
const tagIds = computed({
  get: () => filter.value.tagIds.map(String),
  set: (values: string[]) => patch({tagIds: values.map(Number)}),
})
const groupMatch = computed({
  get: () => filter.value.groupMatch,
  set: (value: string) => patch({groupMatch: value as ResultMatchName}),
})
const tagMatch = computed({
  get: () => filter.value.tagMatch,
  set: (value: string) => patch({tagMatch: value as ResultMatchName}),
})
const ageFrom = computed({
  get: () => filter.value.ageFrom ?? undefined,
  set: (value: number | undefined) => patch({ageFrom: Number.isFinite(value) ? value! : null}),
})
const ageTo = computed({
  get: () => filter.value.ageTo ?? undefined,
  set: (value: number | undefined) => patch({ageTo: Number.isFinite(value) ? value! : null}),
})
const conditions = computed({
  get: () => filter.value.fields,
  set: (value: ResultFilter['fields']) => patch({fields: value}),
})
</script>

<template>
  <div class="grid gap-3 sm:grid-cols-2">
    <div class="space-y-1">
      <FieldLabel>{{ t('forms.analytics.grouping.userTypes') }}</FieldLabel>
      <MultiSelectDropdown v-model="userTypes" :options="userTypeOptions" :placeholder="t('forms.analytics.grouping.choose')"/>
    </div>
    <div class="space-y-1">
      <FieldLabel>{{ t('forms.analytics.grouping.age') }}</FieldLabel>
      <div class="flex items-center gap-2">
        <NumberInput v-model="ageFrom" :placeholder="t('forms.analytics.grouping.from')"/>
        <NumberInput v-model="ageTo" :placeholder="t('forms.analytics.grouping.to')"/>
      </div>
    </div>
    <div class="space-y-1">
      <FieldLabel>{{ t('forms.analytics.grouping.groups') }}</FieldLabel>
      <MultiSelectDropdown v-model="groupIds" :options="groupOptions" :placeholder="t('forms.analytics.grouping.choose')" searchable/>
      <ToggleSwitch v-if="groupIds.length > 1" v-model="groupMatch"
                    :option-a="ResultMatch.ANY" :option-b="ResultMatch.ALL"
                    :label-a="t('forms.analytics.grouping.matchAny')" :label-b="t('forms.analytics.grouping.matchAll')"/>
    </div>
    <div class="space-y-1">
      <FieldLabel>{{ t('forms.analytics.grouping.tags') }}</FieldLabel>
      <MultiSelectDropdown v-model="tagIds" :options="tagOptions" :placeholder="t('forms.analytics.grouping.choose')" searchable/>
      <ToggleSwitch v-if="tagIds.length > 1" v-model="tagMatch"
                    :option-a="ResultMatch.ANY" :option-b="ResultMatch.ALL"
                    :label-a="t('forms.analytics.grouping.matchAny')" :label-b="t('forms.analytics.grouping.matchAll')"/>
    </div>
    <ResultFieldConditions v-if="fields.length" v-model="conditions" :fields="fields" class="sm:col-span-2"/>
  </div>
</template>
