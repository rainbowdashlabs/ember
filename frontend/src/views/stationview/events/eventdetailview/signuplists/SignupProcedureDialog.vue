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
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SignupProcedureForm from './SignupProcedureForm.vue'
import SignupProcedureExisting from './SignupProcedureExisting.vue'
import type {Procedure, ProcedureTemplate} from '@/api/generated/schema'
import type {PartyChoices} from '@/components/procedures/partyChoices'
import type {RestrictionSelection} from '@/api/types'
import {emptyRestriction, toRestriction} from '@/components/input/restriction'
import {procedures} from '@/api'
import {reportCaughtError} from '@/util/devErrorReporter'
import type {SignupMemberSet} from '@/composables/useSignupMemberSet'
import type {Failure} from '@/util/failure'

/**
 * Starts one procedure for each person who holds a place on an appointment, each about that person,
 * all run by the one managing party chosen here.
 *
 * <p>Three things can be behind this window, and only one of them is the form. A station that has
 * written no template cannot have a procedure at all and is told so. A date that has already been
 * prepared for is offered what is there, because a second set of nearly identical procedures for one
 * appointment is a state nobody tidies up.
 */
const visible = defineModel<boolean>({required: true})

const props = defineProps<{
  /** Whether the templates and anything already prepared are still being read. */
  loading: boolean
  creating: boolean
  failure: Failure | null
  memberSet: SignupMemberSet
  /** The occurrence the set belongs to, already written the way a reader reads a date. */
  dateLabel: string
  /** What the list is called before anybody changes it: the appointment and its date. */
  suggestedName: string
  /** The date itself, which is when the preparation has to be done by. */
  occurrenceDate: string | null
  /** The station's templates, without the archived ones. */
  templates: ProcedureTemplate[]
  /** The procedures already prepared for this very date, which are offered instead of a second set. */
  existing: Procedure[]
  /** Whom the managing party can name. */
  partyChoices: PartyChoices
}>()

const emit = defineEmits<{
  (e: 'submit', payload: {templateId: number; name: string; description: string; dueAt: string; party: RestrictionSelection | null}): void
  (e: 'open', procedure: Procedure): void
}>()

const {t} = useI18n()

const selectedTemplate = ref('')
const name = ref('')
const description = ref('')
const dueAt = ref('')
/** The managing party of every procedure, starting as the chosen template's. */
const party = ref<RestrictionSelection>(emptyRestriction())
/** Whether the party shown is the chosen template's or the reader's; until then the template's is left to the server. */
const partyRead = ref(false)
/** Set when somebody has read what is already there and wants a second list anyway. */
const anyway = ref(false)

watch(visible, opened => {
  if (!opened) return
  selectedTemplate.value = ''
  name.value = props.suggestedName
  description.value = ''
  dueAt.value = props.occurrenceDate ?? ''
  party.value = emptyRestriction()
  anyway.value = false
}, {immediate: true})

/**
 * Takes the steps of a template, and with them its description.
 *
 * <p>The order is the point. A template writes both the name and the description, so the name taken
 * from the appointment is put back afterwards; applied before the template it would be thrown away
 * by the very next click. The template's managing party fills in the party, which stays changeable.
 */
async function chooseTemplate(value: string | number | null | undefined) {
  const id = value ? String(value) : ''
  selectedTemplate.value = id
  const template = props.templates.find(entry => String(entry.id) === id)
  description.value = template?.description ?? ''
  name.value = props.suggestedName
  party.value = emptyRestriction()
  partyRead.value = false
  if (!template) return
  try {
    const detail = await procedures.getTemplate(template.id)
    if (selectedTemplate.value !== id) return
    party.value = toRestriction(detail.party)
    partyRead.value = true
  } catch (e) {
    reportCaughtError(e, 'template party for sign-up procedures')
  }
}

/** Takes the party the reader set, which is sent as it stands from then on. */
function chooseParty(chosen: RestrictionSelection) {
  party.value = chosen
  partyRead.value = true
}

const showExisting = computed(() => props.existing.length > 0 && !anyway.value)

function submit() {
  if (!selectedTemplate.value || !name.value.trim()) return
  emit('submit', {
    templateId: Number(selectedTemplate.value),
    name: name.value.trim(),
    description: description.value.trim(),
    dueAt: dueAt.value,
    party: partyRead.value ? party.value : null,
  })
}
</script>

<template>
  <Modal v-model="visible" size="lg">
    <div class="space-y-4">
      <SubHeader>{{ t('signupLists.procedureTitle') }}</SubHeader>

      <Spinner v-if="loading"/>

      <SignupProcedureExisting
          v-else-if="showExisting"
          :existing="existing"
          :date-label="dateLabel"
          @anyway="anyway = true"
          @open="procedure => emit('open', procedure)"
      />

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
          :party="party"
          :party-choices="partyChoices"
          :selected-template="selectedTemplate"
          :templates="templates"
          :creating="creating"
          :failure="failure"
          :member-set="memberSet"
          :date-label="dateLabel"
          @update:party="chooseParty"
          @choose="chooseTemplate"
          @submit="submit"
          @cancel="visible = false"
      />
    </div>
  </Modal>
</template>
