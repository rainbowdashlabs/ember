/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import Alert from '@/components/feedback/Alert.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SignupSetNotes from './SignupSetNotes.vue'
import type {ProcedureTemplate} from '@/api/procedures'
import type {SignupMemberSet} from '@/composables/useSignupMemberSet'

/**
 * What is asked before a preparation list is written: which template its steps come from, what it
 * is called, and by when it has to be done.
 *
 * <p>The template is the one answer that cannot be skipped. Without it the list has no steps, and a
 * row of names with nothing to do is the thing this whole idea exists to stop producing.
 */
const name = defineModel<string>('name', {required: true})
const description = defineModel<string>('description', {required: true})
const dueAt = defineModel<string>('dueAt', {required: true})

const props = defineProps<{
  /** The template picked so far, as the select holds it: its id written out, or nothing. */
  selectedTemplate: string
  templates: ProcedureTemplate[]
  creating: boolean
  error: string
  memberSet: SignupMemberSet
  /** The evening the set belongs to, already written the way a reader reads a date. */
  dateLabel: string
}>()

const emit = defineEmits<{
  (e: 'choose', value: string | number | null | undefined): void
  (e: 'submit'): void
  (e: 'cancel'): void
}>()

const {t} = useI18n()

const canSubmit = computed(() =>
    !props.creating && !!props.selectedTemplate && !!name.value.trim() && props.memberSet.usable)
</script>

<template>
  <div class="space-y-4">
    <div>
      <FieldLabel>{{ t('signupLists.procedureTemplate') }}</FieldLabel>
      <SelectInput
          :model-value="selectedTemplate"
          class="w-full"
          data-testid="signup-procedure-template"
          @update:model-value="emit('choose', $event)"
      >
        <option value="">{{ t('signupLists.procedureTemplatePlaceholder') }}</option>
        <option v-for="template in templates" :key="template.id" :value="String(template.id)">
          {{ template.name }}
        </option>
      </SelectInput>
      <MutedText tag="p" class="mt-1">{{ t('signupLists.procedureTemplateHelp') }}</MutedText>
    </div>

    <div>
      <FieldLabel>{{ t('signupLists.procedureName') }}</FieldLabel>
      <TextInput v-model="name" data-testid="signup-procedure-name"/>
    </div>

    <div>
      <FieldLabel>{{ t('signupLists.procedureDescription') }}</FieldLabel>
      <TextAreaInput v-model="description" data-testid="signup-procedure-description"/>
    </div>

    <div>
      <FieldLabel>{{ t('signupLists.procedureDue') }}</FieldLabel>
      <DateInput v-model="dueAt" data-testid="signup-procedure-due"/>
      <MutedText tag="p" class="mt-1">{{ t('signupLists.procedureDueHelp') }}</MutedText>
    </div>

    <SignupSetNotes :member-set="memberSet" :date-label="dateLabel"/>

    <Alert variant="info">{{ t('signupLists.procedureAssignees', {count: memberSet.count}) }}</Alert>
    <Alert v-if="error" variant="error">{{ error }}</Alert>

    <div class="flex justify-end gap-2 pt-2">
      <SecondaryButton @click="emit('cancel')">{{ t('common.cancel') }}</SecondaryButton>
      <PrimaryButton :disabled="!canSubmit" data-testid="signup-procedure-submit" @click="emit('submit')">
        {{ t('common.create') }}
      </PrimaryButton>
    </div>
  </div>
</template>
