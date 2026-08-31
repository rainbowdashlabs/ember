/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Alert from '@/components/feedback/Alert.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import ChecklistFormModal from '@/views/stationview/checklist/checklistmodals/ChecklistFormModal.vue'
import SignupSetNotes from './SignupSetNotes.vue'
import type {SignupMemberSet} from '@/composables/useSignupMemberSet'

/**
 * Names the list and its first column before it is made.
 *
 * <p>The column is not an extra: a checklist without one is refused outright, so asking for it here
 * is the difference between landing on a usable grid and landing on an error. The description the
 * ordinary create dialog carries stays, because a list made from a sign-up sheet is exactly the kind
 * that wants a note saying which evening it came from.
 */
const visible = defineModel<boolean>({required: true})

const props = defineProps<{
  creating: boolean
  error: string
  memberSet: SignupMemberSet
  /** The evening the set belongs to, already written the way a reader reads a date. */
  dateLabel: string
  /** What the list is called before anybody changes it: the appointment and its date. */
  suggestedName: string
}>()

const emit = defineEmits<{
  (e: 'submit', payload: {name: string; description: string; column: string}): void
}>()

const {t} = useI18n()

const name = ref('')
const description = ref('')
const column = ref('')

watch(visible, opened => {
  if (!opened) return
  name.value = props.suggestedName
  description.value = ''
  column.value = ''
}, {immediate: true})

function submit() {
  if (!name.value.trim() || !column.value.trim()) return
  emit('submit', {
    name: name.value.trim(),
    description: description.value.trim(),
    column: column.value.trim(),
  })
}
</script>

<template>
  <ChecklistFormModal
      v-model="visible"
      v-model:name="name"
      v-model:description="description"
      :title="t('signupLists.checklistTitle')"
      size="lg"
      :submit-disabled="creating || !name.trim() || !column.trim()"
      @submit="submit"
  >
    <div>
      <FieldLabel>{{ t('signupLists.firstColumn') }}</FieldLabel>
      <TextInput v-model="column" :placeholder="t('checklist.columnLabelPlaceholder')" data-testid="signup-checklist-column"/>
      <MutedText tag="p" class="mt-1">{{ t('signupLists.firstColumnHelp') }}</MutedText>
    </div>

    <SignupSetNotes :member-set="memberSet" :date-label="dateLabel"/>

    <Alert v-if="error" variant="error">{{ error }}</Alert>
  </ChecklistFormModal>
</template>
