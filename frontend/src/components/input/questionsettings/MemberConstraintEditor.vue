/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import LabelledField from '@/components/input/LabelledField.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import {memberConstraintOf} from '@/api/fieldTypes'
import {StationUserType, StationUserTypeLabels} from '@/api/types'
import type {MemberGroup, StationUserType as StationUserTypeName, UserTag} from '@/api/generated/schema'

/**
 * The group, user type or tag a member field is narrowed to, whichever its type names.
 *
 * <p>The server refuses a member outside it wherever the field is answered, so this is not a hint
 * for the picker but the field's rule.
 */
const groupId = defineModel<number | null | undefined>('groupId', {required: true})
const userType = defineModel<StationUserTypeName | null | undefined>('userType', {required: true})
const tagId = defineModel<number | null | undefined>('tagId', {required: true})

const props = defineProps<{
  fieldType: string
  groups?: MemberGroup[]
  tags?: UserTag[]
}>()

const {t} = useI18n()

const constraint = computed(() => memberConstraintOf(props.fieldType))

const userTypeOptions = Object.values(StationUserType).map(value => ({value, label: StationUserTypeLabels[value]}))

function idOrNull(value: unknown): number | null {
  return value === '' || value === null || value === undefined ? null : Number(value)
}

function userTypeOrNull(value: unknown): StationUserTypeName | null {
  return userTypeOptions.find(option => option.value === value)?.value ?? null
}
</script>

<template>
  <LabelledField v-if="constraint === 'group'" :label="t('questionSettings.group')">
    <SelectInput :model-value="groupId == null ? '' : String(groupId)" class="w-full sm:w-auto"
                 data-testid="question-group" @update:model-value="value => groupId = idOrNull(value)">
      <option value="">{{ t('questionSettings.selectGroup') }}</option>
      <option v-for="group in groups ?? []" :key="group.id" :value="String(group.id)">{{ group.name }}</option>
    </SelectInput>
  </LabelledField>

  <LabelledField v-else-if="constraint === 'userType'" :label="t('questionSettings.userType')">
    <SelectInput :model-value="userType ?? ''" class="w-full sm:w-auto" data-testid="question-user-type"
                 @update:model-value="value => userType = userTypeOrNull(value)">
      <option value="">{{ t('questionSettings.selectUserType') }}</option>
      <option v-for="option in userTypeOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
    </SelectInput>
  </LabelledField>

  <LabelledField v-else-if="constraint === 'tag'" :label="t('questionSettings.tag')">
    <SelectInput :model-value="tagId == null ? '' : String(tagId)" class="w-full sm:w-auto"
                 data-testid="question-tag" @update:model-value="value => tagId = idOrNull(value)">
      <option value="">{{ t('questionSettings.selectTag') }}</option>
      <option v-for="tag in tags ?? []" :key="tag.id" :value="String(tag.id)">{{ tag.name }}</option>
    </SelectInput>
  </LabelledField>
</template>
