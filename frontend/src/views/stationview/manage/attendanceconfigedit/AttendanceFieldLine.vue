/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import CompactQuestionRow from '@/components/input/questionsettings/CompactQuestionRow.vue'
import MarkToggle from '@/components/input/questionsettings/MarkToggle.vue'
import {fieldTypeLabel, namesMembers} from '@/api/fieldTypes'
import type {MemberGroup} from '@/api/generated/schema'
import FieldEditor from './FieldEditor.vue'
import {offersOf, type AttendanceFieldDraft} from './attendanceFieldDraft'

/**
 * One field of an attendance sheet as a single line: its name, its type, whether it must be answered
 * and, for a field naming members, whether those members count as present. The rest of its card opens
 * on request; a field without a name yet opens straight away, since it has to be set up first.
 */
const field = defineModel<AttendanceFieldDraft>({required: true})

defineProps<{
  availableGroups: MemberGroup[]
  /** Shown only, as on a help page. */
  disabled?: boolean
}>()

const emit = defineEmits<{
  remove: []
}>()

const {t} = useI18n()

const open = ref(field.value.name.trim() === '')

const asksRequired = computed(() => offersOf(field.value.fieldType).includes('required'))
const namesPeople = computed(() => namesMembers(field.value.fieldType))

function update(patch: Partial<AttendanceFieldDraft>) {
  field.value = {...field.value, ...patch}
}
</script>

<template>
  <CompactQuestionRow
      v-model:open="open"
      :name="field.name"
      :name-label="t('attendanceConfig.fieldName')"
      :type-label="fieldTypeLabel(t, field.fieldType)"
      :disabled="disabled"
      @update:name="name => update({name})"
      @remove="emit('remove')"
  >
    <template #marks>
      <MarkToggle
          v-if="asksRequired"
          :model-value="field.settings.required ?? false"
          :icon="['fas', 'asterisk']"
          :label="t('questionSettings.required')"
          :disabled="disabled"
          data-testid="attendance-field-required"
          @update:model-value="required => update({settings: {...field.settings, required}})"
      />
      <MarkToggle
          v-if="namesPeople"
          :model-value="field.autoAttend"
          :icon="['fas', 'user-check']"
          :label="t('attendanceConfig.fieldAutoAttend')"
          :disabled="disabled"
          data-testid="attendance-field-auto-attend"
          @update:model-value="autoAttend => update({autoAttend})"
      />
    </template>

    <FieldEditor v-model="field" :available-groups="availableGroups" @remove="emit('remove')"/>
  </CompactQuestionRow>
</template>
