/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ActionsMenu from '@/components/button/ActionsMenu.vue'
import DropdownMenuItem from '@/components/button/DropdownMenuItem.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SignupChecklistDialog from './SignupChecklistDialog.vue'
import SignupProcedureDialog from './SignupProcedureDialog.vue'
import SignupSurveyDialog from './SignupSurveyDialog.vue'
import {noPartyChoices, type PartyChoices} from './partyChoices'
import {checklists, forms, procedures, stationMembers} from '@/api'
import {audienceLists} from '@/components/documents/generation'
import type {RestrictionSelection} from '@/api/types'
import {
  FormPurpose,
  StationModule,
  StationPermission,
  type Procedure,
  type ProcedureTemplate,
  type StationEvent,
} from '@/api/generated/schema'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useSession} from '@/composables/useSession'
import type {SignupMemberSet} from '@/composables/useSignupMemberSet'
import {describeFailure} from '@/util/failure'
import {dateToInstant, formatDate} from '@/util/format'
import {showToast} from '@/util/toast'

const props = defineProps<{
  event: StationEvent
  /** The date the sign-ups are read from, which is the one the screen is focused on. */
  effectiveDate: string | null
  memberSet: SignupMemberSet
}>()

const {t} = useI18n()
const router = useRouter()
const {hasPermission, isModuleEnabled, actingStation} = useSession()

/**
 * An association's calendar is a station's calendar, borrowed for as long as the screen is open. The
 * association has no lists of its own, so an entry here would either lead nowhere or drop the reader
 * out of the association and open the new list under a station they were not looking at.
 */
const insideCluster = computed(() => actingStation() !== null)

const canCreateChecklist = computed(() => hasPermission(StationPermission.CHECKLIST_MANAGE))

/**
 * A survey needs two things where a checklist needs one: the right to make one, and the survey
 * feature being switched on for this station at all. Checklists cannot be switched off, surveys can.
 */
const canCreateSurvey = computed(() =>
    hasPermission(StationPermission.POLL_CREATE) && isModuleEnabled(StationModule.FORMS))

/**
 * A procedure is gated the same way a survey is, on a permission and on the feature being switched
 * on for this station. Checklists are the odd one out here: they have no module to switch off.
 */
const canCreateProcedure = computed(() =>
    hasPermission(StationPermission.PROCEDURE_EDIT) && isModuleEnabled(StationModule.PROCEDURES))

/**
 * Where the menu can do anything at all: an appointment that is signed up for, a date in view,
 * a panel that is not the association's, and at least one thing the reader may create.
 */
const shown = computed(() =>
    !!props.event.requiresRegistration
    && !!props.effectiveDate
    && !insideCluster.value
    && (canCreateChecklist.value || canCreateSurvey.value || canCreateProcedure.value))

const dateLabel = computed(() => formatDate(props.effectiveDate))

const suggestedName = computed(() =>
    t('signupLists.namePrefill', {event: props.event.name ?? '', date: dateLabel.value}))

const showChecklist = ref(false)
const showSurvey = ref(false)
const showProcedure = ref(false)

const procedureTemplates = ref<ProcedureTemplate[]>([])
/** What has already been prepared for this date, so a second press does not make a second set. */
const existingProcedures = ref<Procedure[]>([])
/** Whom a party can name, read as the procedure dialog opens. */
const partyChoices = ref<PartyChoices>(noPartyChoices())

/**
 * Makes the list, either as a copy of the names as they stand or as one tied to this date.
 *
 * <p>A following list names the occurrence instead of the people, and the server resolves the same
 * accepted sign-ups from it, so the two agree on the first day and only the following one can still
 * agree on the next.
 */
const {running: creating, failure: checklistFailure, run: runCreateChecklist} = useAsyncAction(
    async (payload: {name: string; description: string; column: string; following: boolean}) => {
      const following = payload.following && props.effectiveDate !== null
      const detail = await checklists.createChecklist({
        name: payload.name,
        description: payload.description,
        columns: [{label: payload.column}],
        restriction: {
          userTypes: [],
          groupIds: [],
          tagIds: [],
          memberIds: following ? [] : props.memberSet.memberIds,
          mode: 'OR',
        },
        ...(following && props.effectiveDate
            ? {source: {eventId: props.event.id, date: props.effectiveDate}}
            : {}),
      })
      showChecklist.value = false
      showToast(t('signupLists.checklistCreated', {count: props.memberSet.count}), 'success')
      await router.push({name: 'checklist-detail', params: {id: detail.id}})
    },
    {formatError: (e) => describeFailure(e, t).message},
)

/**
 * Making a survey is three steps, not one.
 *
 * <p>The form is written first and carries a purpose, which decides the kinds of question it may
 * ask; an internal one is the only sensible answer for a survey of the people who were there. Who it
 * is meant for is a second call. It stays a draft until somebody publishes it, and that is left to
 * the person, because a survey with no questions in it is not one anybody should be sent.
 */
const {running: creatingSurvey, failure: surveyFailure, run: runCreateSurvey} = useAsyncAction(
    async (payload: {name: string}) => {
      const form = await forms.createForm({title: payload.name, purpose: FormPurpose.INTERNAL})
      await forms.setRestrictions(form.id, {
        userTypes: [],
        groupIds: [],
        tagIds: [],
        memberIds: props.memberSet.memberIds,
        mode: 'OR',
      })
      showSurvey.value = false
      showToast(t('signupLists.surveyCreated', {count: props.memberSet.count}), 'success')
      await router.push({name: 'forms-edit', params: {id: form.id}})
    },
    {formatError: (e) => describeFailure(e, t).message},
)

/**
 * What the dialog needs before it can offer anything: the templates it must pick one of, and
 * whatever was already prepared for this same date.
 */
const {running: loadingProcedure, failure: procedureLoadFailure, run: runLoadProcedure} = useAsyncAction(
    async () => {
      const [templates, prepared, members, lists] = await Promise.all([
        procedures.getTemplates(),
        procedures.getProceduresForEvent(props.event.id, props.effectiveDate!),
        stationMembers.listCompletions(),
        audienceLists(),
      ])
      procedureTemplates.value = templates.filter(template => !template.archived)
      existingProcedures.value = prepared
      partyChoices.value = {members, groups: lists.groups, tags: lists.tags}
    },
    {formatError: (e) => describeFailure(e, t).message},
)

async function openProcedure() {
  procedureTemplates.value = []
  existingProcedures.value = []
  showProcedure.value = true
  await runLoadProcedure()
}

function openExistingProcedure(procedure: Procedure) {
  showProcedure.value = false
  return router.push({name: 'procedure-detail', params: {id: procedure.id}})
}

/**
 * Starts one procedure for each person who holds a place, each about that person, all run by the one
 * party chosen in the dialog, which starts as the template's; where the template's could not be read yet
 * none is sent, so the server takes it. The procedures keep the template's steps as
 * they are: the party ticks them, and the person each one is about reads it.
 */
const {running: creatingProcedure, failure: procedureFailure, run: runCreateProcedure} = useAsyncAction(
    async (payload: {templateId: number; name: string; description: string; dueAt: string; party: RestrictionSelection | null}) => {
      const created = await procedures.createProceduresForMembers({
        templateId: payload.templateId,
        name: payload.name,
        description: payload.description || undefined,
        dueAt: dateToInstant(payload.dueAt) ?? undefined,
        isPublic: true,
        party: payload.party ?? undefined,
        eventId: props.event.id,
        eventDate: props.effectiveDate ?? undefined,
        memberIds: props.memberSet.memberIds,
      })
      showProcedure.value = false
      showToast(t('signupLists.procedureCreated', {count: created.length}), 'success')
      await router.push({name: 'procedure-list'})
    },
    {formatError: (e) => describeFailure(e, t).message},
)
</script>

<template>
  <ActionsMenu v-if="shown" :label="t('signupLists.menu')" data-testid="signup-lists-menu">
    <MutedText tag="div" class="px-4 py-2">
      {{ memberSet.usable ? t('signupLists.menuIntro', {date: dateLabel, count: memberSet.count}) : t('signupLists.menuNobody') }}
    </MutedText>
    <DropdownMenuItem
        v-if="canCreateChecklist"
        :icon="['fas', 'list-check']"
        :disabled="!memberSet.usable || creating"
        data-testid="signup-checklist-entry"
        @click="showChecklist = true"
    >
      {{ t('signupLists.checklist') }}
    </DropdownMenuItem>
    <DropdownMenuItem
        v-if="canCreateSurvey"
        :icon="['fas', 'square-poll-vertical']"
        :disabled="!memberSet.usable || creatingSurvey"
        data-testid="signup-survey-entry"
        @click="showSurvey = true"
    >
      {{ t('signupLists.survey') }}
    </DropdownMenuItem>
    <DropdownMenuItem
        v-if="canCreateProcedure"
        :icon="['fas', 'diagram-project']"
        :disabled="!memberSet.usable || creatingProcedure"
        data-testid="signup-procedure-entry"
        @click="openProcedure"
    >
      {{ t('signupLists.procedure') }}
    </DropdownMenuItem>
  </ActionsMenu>

  <SignupChecklistDialog
      v-model="showChecklist"
      :creating="creating"
      :failure="checklistFailure"
      :member-set="memberSet"
      :date-label="dateLabel"
      :suggested-name="suggestedName"
      @submit="runCreateChecklist"
  />

  <SignupSurveyDialog
      v-model="showSurvey"
      :creating="creatingSurvey"
      :failure="surveyFailure"
      :member-set="memberSet"
      :date-label="dateLabel"
      :suggested-name="suggestedName"
      @submit="runCreateSurvey"
  />

  <SignupProcedureDialog
      v-model="showProcedure"
      :loading="loadingProcedure"
      :creating="creatingProcedure"
      :failure="procedureLoadFailure ?? procedureFailure"
      :member-set="memberSet"
      :date-label="dateLabel"
      :suggested-name="suggestedName"
      :occurrence-date="effectiveDate"
      :templates="procedureTemplates"
      :existing="existingProcedures"
      :party-choices="partyChoices"
      @submit="runCreateProcedure"
      @open="openExistingProcedure"
  />
</template>
