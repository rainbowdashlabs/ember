/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import QuestionCard from '@/components/input/questionsettings/QuestionCard.vue'
import WidthField from '@/components/profilefields/WidthField.vue'
import AutoAttendToggle from './AutoAttendToggle.vue'
import {namesMembers, OfferedFieldTypes} from '@/api/fieldTypes'
import type {MemberGroup} from '@/api/generated/schema'
import {offersOf, type AttendanceFieldDraft} from './attendanceFieldDraft'

/**
 * One field of an attendance sheet, edited where it stands in the list.
 *
 * <p>The shared question card holds the name, the type and the settings every field has. What only
 * a sheet's field has comes under it: how wide it is drawn, and for a member field whether the
 * members it names are counted as present.
 */
const field = defineModel<AttendanceFieldDraft>({required: true})

defineProps<{
  availableGroups: MemberGroup[]
}>()

const emit = defineEmits<{
  remove: []
}>()

const {t} = useI18n()

const offers = computed(() => offersOf(field.value.fieldType))
const namesPeople = computed(() => namesMembers(field.value.fieldType))

function update(patch: Partial<AttendanceFieldDraft>) {
  field.value = {...field.value, ...patch}
}
</script>

<template>
  <QuestionCard
      :default-hint="t('attendanceConfig.fieldDefaultValueHint')"
      :field-type="field.fieldType"
      :groups="availableGroups"
      :name="field.name"
      :name-label="t('attendanceConfig.fieldName')"
      :name-placeholder="t('attendanceConfig.fieldNamePlaceholder')"
      :offers="offers"
      :settings="field.settings"
      :types="OfferedFieldTypes.ATTENDANCE"
      describe
      @remove="emit('remove')"
      @update:field-type="fieldType => update({fieldType})"
      @update:name="name => update({name})"
      @update:settings="settings => update({settings})"
  >
    <AutoAttendToggle v-if="namesPeople" :auto-attend="field.autoAttend"
                      @update:auto-attend="autoAttend => update({autoAttend})"/>
    <div class="w-40">
      <WidthField :model-value="field.width" @update:model-value="width => update({width})"/>
    </div>
  </QuestionCard>
</template>
