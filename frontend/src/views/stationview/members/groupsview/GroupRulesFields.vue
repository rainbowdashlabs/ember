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
import type {GroupHolder} from './useGroupRulesForm'
import BulletList from '@/components/typography/BulletList.vue'
import Alert from '@/components/feedback/Alert.vue'
import {memberDisplayName} from '../listview/useMemberData'
import {STATION_USER_TYPES} from '@/util/stationUserTypes'

/**
 * The rules of a group in its form: the set it belongs to, and the member types it takes. No type
 * ticked means every type, which is what a group has always been.
 */
withDefaults(defineProps<{
  sets: MemberGroupSet[]
  /** The members of the group of none of the ticked types, who saving would take out. */
  misfits?: GroupHolder[]
}>(), {
  misfits: () => [],
})

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
    <Alert v-if="misfits.length > 0" variant="info" data-testid="group-misfits">
      <p>{{ t('memberGroups.rules.misfits', {count: misfits.length}) }}</p>
      <BulletList>
        <li v-for="member in misfits" :key="member.id">
          <NuxtLink :to="{name: 'members-edit', params: {id: member.id}}" class="text-primary hover:underline">
            {{ memberDisplayName(member) }}
          </NuxtLink>
        </li>
      </BulletList>
    </Alert>
  </div>
</template>
