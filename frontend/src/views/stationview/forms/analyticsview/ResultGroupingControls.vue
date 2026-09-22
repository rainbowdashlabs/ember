/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import MultiSelectDropdown from '@/components/input/select/MultiSelectDropdown.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {ResultDimension, type ResultDimensionName, type ResultGrouping} from '@/api/forms'
import {FieldTypes, type ProfileField} from '@/api/profileFields'
import {StationUserType, StationUserTypeLabels, type MemberGroup, type UserTag} from '@/api/types'
import {groupingBy, NO_VALUE_GROUP, parseBounds} from './resultQuery'

/**
 * What to split the results by, and optionally which of the groups to compare.
 *
 * Choosing groups A and B compares exactly those two side by side. Age and number fields are split
 * into brackets whose starting points the reader types in.
 */
const props = defineProps<{
  groups: MemberGroup[]
  tags: UserTag[]
  fields: ProfileField[]
}>()

const grouping = defineModel<ResultGrouping | null>({required: true})

const {t} = useI18n()

const FIELD_PREFIX = 'FIELD:'

const dimension = computed({
  get: () => {
    const current = grouping.value
    if (!current) return ''
    return current.by === ResultDimension.FIELD ? `${FIELD_PREFIX}${current.fieldId}` : current.by
  },
  set: (value: string | number | null | undefined) => {
    const choice = String(value ?? '')
    if (!choice) grouping.value = null
    else if (choice.startsWith(FIELD_PREFIX)) grouping.value = groupingBy(ResultDimension.FIELD, Number(choice.slice(FIELD_PREFIX.length)))
    else grouping.value = groupingBy(choice as ResultDimensionName)
  },
})

const field = computed(() => props.fields.find(f => f.id === grouping.value?.fieldId))
const usesBrackets = computed(() => grouping.value?.by === ResultDimension.AGE
    || (grouping.value?.by === ResultDimension.FIELD && field.value?.fieldType === FieldTypes.NUMBER))

const onlyOptions = computed(() => {
  switch (grouping.value?.by) {
    case ResultDimension.USER_TYPE: return Object.values(StationUserType).map(type => ({value: type, label: StationUserTypeLabels[type]}))
    case ResultDimension.GROUP: return [...props.groups.map(g => ({value: String(g.id), label: g.name ?? ''})), none('GROUP')]
    case ResultDimension.TAG: return [...props.tags.map(tag => ({value: String(tag.id), label: tag.name})), none('TAG')]
    case ResultDimension.FIELD: return field.value?.fieldType === FieldTypes.BOOLEAN
        ? [{value: 'true', label: t('forms.analytics.grouping.yes')}, {value: 'false', label: t('forms.analytics.grouping.no')}]
        : ((field.value?.config?.options as string[] | undefined) ?? []).map(option => ({value: option, label: option}))
    default: return []
  }
})

function none(by: string) {
  return {value: NO_VALUE_GROUP, label: t(`forms.analytics.grouping.none.${by}`)}
}

const only = computed({
  get: () => grouping.value?.only ?? [],
  set: (values: string[]) => { if (grouping.value) grouping.value = {...grouping.value, only: values} },
})

const boundsText = ref('')
watch(() => grouping.value?.bounds, bounds => { boundsText.value = (bounds ?? []).join(', ') }, {immediate: true})

function applyBounds() {
  if (!grouping.value) return
  grouping.value = {...grouping.value, bounds: parseBounds(boundsText.value)}
}
</script>

<template>
  <div class="grid gap-3 sm:grid-cols-2">
    <div class="space-y-1">
      <FieldLabel>{{ t('forms.analytics.grouping.groupBy') }}</FieldLabel>
      <SelectInput v-model="dimension">
        <option value="">{{ t('forms.analytics.grouping.noGrouping') }}</option>
        <option :value="ResultDimension.USER_TYPE">{{ t('forms.analytics.grouping.byUserType') }}</option>
        <option :value="ResultDimension.GROUP">{{ t('forms.analytics.grouping.byGroup') }}</option>
        <option :value="ResultDimension.TAG">{{ t('forms.analytics.grouping.byTag') }}</option>
        <option :value="ResultDimension.AGE">{{ t('forms.analytics.grouping.byAge') }}</option>
        <option v-for="f in fields" :key="f.id" :value="`${FIELD_PREFIX}${f.id}`">{{ f.name }}</option>
      </SelectInput>
    </div>
    <div v-if="usesBrackets" class="space-y-1">
      <FieldLabel>{{ t('forms.analytics.grouping.bounds') }}</FieldLabel>
      <TextInput v-model="boundsText" @blur="applyBounds" @keydown.enter="applyBounds"/>
      <MutedText size="xs" tag="p">{{ t('forms.analytics.grouping.boundsHint') }}</MutedText>
    </div>
    <div v-else-if="onlyOptions.length" class="space-y-1">
      <FieldLabel>{{ t('forms.analytics.grouping.only') }}</FieldLabel>
      <MultiSelectDropdown v-model="only" :options="onlyOptions" :placeholder="t('forms.analytics.grouping.onlyPlaceholder')" searchable/>
    </div>
  </div>
</template>
