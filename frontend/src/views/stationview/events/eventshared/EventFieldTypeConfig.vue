/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import {EventFieldTypes} from '@/api/events'
import {StationUserType, StationUserTypeLabels, type MemberGroup, type StationUserTypeName, type UserTag} from '@/api/types'
import {fieldConstraint, isMemberFieldType} from './eventFieldConfig'

const props = defineProps<{
  fieldType: string
  groups?: MemberGroup[]
  tags?: UserTag[]
}>()

const enumOptions = defineModel<string>('enumOptions', {required: true})
const groupId = defineModel<string>('groupId', {required: true})
const userType = defineModel<string>('userType', {required: true})
const tagId = defineModel<string>('tagId', {required: true})
const selfRegistration = defineModel<boolean>('selfRegistration', {required: true})

const {t} = useI18n()

const userTypeOptions = Object.values(StationUserType).map(v => ({
  value: v,
  label: StationUserTypeLabels[v as StationUserTypeName],
}))

const constraint = computed(() => fieldConstraint(props.fieldType))
const isMemberField = computed(() => isMemberFieldType(props.fieldType))
</script>

<template>
  <div v-if="fieldType === EventFieldTypes.ENUM" class="space-y-1">
    <FieldLabel>{{ t('eventFields.enumOptions') }}</FieldLabel>
    <TextAreaInput v-model="enumOptions" :placeholder="t('eventFields.enumOptionsPlaceholder')" :rows="3"/>
  </div>

  <div v-if="constraint === 'group' && groups && groups.length > 0" class="space-y-1">
    <FieldLabel>{{ t('eventFields.group') }}</FieldLabel>
    <SelectInput v-model="groupId" class="w-full sm:w-auto" data-testid="event-field-group">
      <option value="">{{ t('eventFields.selectGroup') }}</option>
      <option v-for="g in groups" :key="g.id" :value="String(g.id)">{{ g.name }}</option>
    </SelectInput>
  </div>

  <div v-if="constraint === 'userType'" class="space-y-1">
    <FieldLabel>{{ t('eventFields.userType') }}</FieldLabel>
    <SelectInput v-model="userType" class="w-full sm:w-auto">
      <option value="">{{ t('eventFields.selectUserType') }}</option>
      <option v-for="ut in userTypeOptions" :key="ut.value" :value="ut.value">{{ ut.label }}</option>
    </SelectInput>
  </div>

  <div v-if="constraint === 'tag' && tags && tags.length > 0" class="space-y-1">
    <FieldLabel>{{ t('eventFields.tag') }}</FieldLabel>
    <SelectInput v-model="tagId" class="w-full sm:w-auto">
      <option value="">{{ t('eventFields.selectTag') }}</option>
      <option v-for="tg in tags" :key="tg.id" :value="String(tg.id)">{{ tg.name }}</option>
    </SelectInput>
  </div>

  <label v-if="isMemberField" class="flex items-center gap-2 text-sm">
    <ToggleInput v-model="selfRegistration"/>
    {{ t('eventFields.selfRegistration') }}
  </label>
</template>
