/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Alert from '@/components/feedback/Alert.vue'
import Modal from '@/components/feedback/Modal.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SignupProcedureForm from './SignupProcedureForm.vue'
import type {Procedure, ProcedureTemplate} from '@/api/procedures'
import type {SignupMemberSet} from '@/composables/useSignupMemberSet'

/**
 * Prepares one shared list of steps for the people who hold a place on an evening.
 *
 * <p>A procedure is not a list per person: every step carries one tick that counts for everybody on
 * it. So this makes one preparation list a handful of people work through together, and the count
 * it shows is how many of them will be told about it, one message each.
 *
 * <p>Three things can be behind this window, and only one of them is the form. A station that has
 * written no template cannot have a procedure at all and is told so. An evening that has already
 * been prepared for is offered what is there, because two nearly identical lists for one appointment
 * is a state nobody tidies up.
 */
const visible = defineModel<boolean>({required: true})

const props = defineProps<{
  /** Whether the templates and anything already prepared are still being read. */
  loading: boolean
  creating: boolean
  error: string
  memberSet: SignupMemberSet
  /** The evening the set belongs to, already written the way a reader reads a date. */
  dateLabel: string
  /** What the list is called before anybody changes it: the appointment and its date. */
  suggestedName: string
  /** The evening itself, which is when the preparation has to be done by. */
  occurrenceDate: string | null
  /** The station's templates, without the archived ones. */
  templates: ProcedureTemplate[]
  /** A list already prepared for this very evening, which is offered instead of a second one. */
  existing: Procedure | null
}>()

const emit = defineEmits<{
  (e: 'submit', payload: {templateId: number; name: string; description: string; dueAt: string}): void
  (e: 'open', procedure: Procedure): void
}>()

const {t} = useI18n()

const selectedTemplate = ref('')
const name = ref('')
const description = ref('')
const dueAt = ref('')
/** Set when somebody has read what is already there and wants a second list anyway. */
const anyway = ref(false)

watch(visible, opened => {
  if (!opened) return
  selectedTemplate.value = ''
  name.value = props.suggestedName
  description.value = ''
  dueAt.value = props.occurrenceDate ?? ''
  anyway.value = false
}, {immediate: true})

/**
 * Takes the steps of a template, and with them its description.
 *
 * <p>The order is the point. A template writes both the name and the description, so the name taken
 * from the appointment is put back afterwards; applied before the template it would be thrown away
 * by the very next click.
 */
function chooseTemplate(value: string | number | null | undefined) {
  const id = value ? String(value) : ''
  selectedTemplate.value = id
  const template = props.templates.find(entry => String(entry.id) === id)
  description.value = template?.description ?? ''
  name.value = props.suggestedName
}

const showExisting = computed(() => !!props.existing && !anyway.value)

function submit() {
  if (!selectedTemplate.value || !name.value.trim()) return
  emit('submit', {
    templateId: Number(selectedTemplate.value),
    name: name.value.trim(),
    description: description.value.trim(),
    dueAt: dueAt.value,
  })
}
</script>

<template>
  <Modal v-model="visible" size="lg">
    <div class="space-y-4">
      <SubHeader>{{ t('signupLists.procedureTitle') }}</SubHeader>

      <Spinner v-if="loading"/>

      <template v-else-if="showExisting">
        <Alert variant="info" data-testid="signup-procedure-existing">
          {{ t('signupLists.procedureExists', {name: existing!.name, date: dateLabel}) }}
        </Alert>
        <div class="flex flex-wrap justify-end gap-2 pt-2">
          <SecondaryButton @click="anyway = true">{{ t('signupLists.procedureAnyway') }}</SecondaryButton>
          <PrimaryButton data-testid="signup-procedure-open" @click="emit('open', existing!)">
            {{ t('signupLists.procedureOpen') }}
          </PrimaryButton>
        </div>
      </template>

      <template v-else-if="templates.length === 0">
        <Alert variant="info" data-testid="signup-procedure-no-templates">
          {{ t('signupLists.procedureNoTemplates') }}
        </Alert>
        <div class="flex justify-end pt-2">
          <SecondaryButton @click="visible = false">{{ t('common.close') }}</SecondaryButton>
        </div>
      </template>

      <SignupProcedureForm
          v-else
          v-model:name="name"
          v-model:description="description"
          v-model:due-at="dueAt"
          :selected-template="selectedTemplate"
          :templates="templates"
          :creating="creating"
          :error="error"
          :member-set="memberSet"
          :date-label="dateLabel"
          @choose="chooseTemplate"
          @submit="submit"
          @cancel="visible = false"
      />
    </div>
  </Modal>
</template>
