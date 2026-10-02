/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import {StationUserTypeLabels} from '@/api/types'
import type {MemberGroupSet, StationUserType} from '@/api/generated/schema'
import {STATION_USER_TYPES} from '@/util/stationUserTypes'

/**
 * The rules of a group in its form: the set it belongs to, and the member types it takes. No type
 * ticked means every type, which is what a group has always been.
 */
defineProps<{
  sets: MemberGroupSet[]
}>()

const setId = defineModel<number | null>('setId', {required: true})
const userTypes = defineModel<StationUserType[]>('userTypes', {required: true})

const {t} = useI18n()

const types = STATION_USER_TYPES

function toggle(type: StationUserType) {
  userTypes.value = userTypes.value.includes(type)
      ? userTypes.value.filter(held => held !== type)
      : [...userTypes.value, type]
}

function pickSet(value: unknown) {
  setId.value = value === '' || value == null ? null : Number(value)
}
</script>

<template>
  <div class="space-y-1">
    <FieldLabel>{{ t('memberGroups.rules.set') }}</FieldLabel>
    <SelectInput :model-value="setId ?? ''" @update:model-value="pickSet">
      <option value="">{{ t('memberGroups.rules.noSet') }}</option>
      <option v-for="set in sets" :key="set.id" :value="set.id">{{ set.name }}</option>
    </SelectInput>
    <MutedText tag="p">{{ t('memberGroups.rules.setHint') }}</MutedText>
  </div>
  <div class="space-y-1">
    <FieldLabel>{{ t('memberGroups.rules.userTypes') }}</FieldLabel>
    <div class="flex flex-wrap gap-2">
      <SelectionToggleButton v-for="type in types" :key="type" :selected="userTypes.includes(type)" @toggle="toggle(type)">
        {{ StationUserTypeLabels[type] }}
      </SelectionToggleButton>
    </div>
    <MutedText tag="p">{{ t('memberGroups.rules.userTypesHint') }}</MutedText>
  </div>
</template>
