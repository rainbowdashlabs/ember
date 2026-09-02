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
import {checklists, forms, procedures} from '@/api'
import type {StationEvent} from '@/api/events'
import {FormPurpose} from '@/api/forms'
import type {Procedure, ProcedureTemplate} from '@/api/procedures'
import {StationModules, StationPermission} from '@/api/types'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useSession} from '@/composables/useSession'
import type {SignupMemberSet} from '@/composables/useSignupMemberSet'
import {getActingStation} from '@/util/actingStationState'
import {dateToInstant, formatDate} from '@/util/format'
import {showToast} from '@/util/toast'

const props = defineProps<{
  event: StationEvent
  /** The evening the sign-ups are read from, which is the one the screen is focused on. */
  effectiveDate: string | null
  memberSet: SignupMemberSet
}>()

const {t} = useI18n()
const router = useRouter()
const {hasPermission, isModuleEnabled} = useSession()

/**
 * An association's calendar is a station's calendar, borrowed for as long as the screen is open. The
 * association has no lists of its own, so an entry here would either lead nowhere or drop the reader
 * out of the association and open the new list under a station they were not looking at.
 */
const insideCluster = computed(() => getActingStation() !== null)

const canCreateChecklist = computed(() => hasPermission(StationPermission.CHECKLIST_MANAGE))

/**
 * A survey needs two things where a checklist needs one: the right to make one, and the survey
 * feature being switched on for this station at all. Checklists cannot be switched off, surveys can.
 */
const canCreateSurvey = computed(() =>
    hasPermission(StationPermission.POLL_CREATE) && isModuleEnabled(StationModules.FORMS))

/**
 * A procedure is gated the same way a survey is, on a permission and on the feature being switched
 * on for this station. Checklists are the odd one out here: they have no module to switch off.
 */
const canCreateProcedure = computed(() =>
    hasPermission(StationPermission.PROCEDURE_EDIT) && isModuleEnabled(StationModules.PROCEDURES))

/**
 * Where the menu can do anything at all: an appointment that is signed up for, an evening in view,
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
/** What has already been prepared for this evening, so a second press does not make a second list. */
const existingProcedure = ref<Procedure | null>(null)

/**
 * Makes the list, either as a copy of tonight's names or as one tied to this evening.
 *
 * <p>A following list names the occurrence instead of the people, and the server resolves the same
 * accepted sign-ups from it, so the two agree on the first day and only the following one can still
 * agree on the next.
 */
const {running: creating, error, run: runCreateChecklist} = useAsyncAction(
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
    {formatError: () => t('signupLists.createError')},
)

/**
 * Making a survey is three steps, not one.
 *
 * <p>The form is written first and carries a purpose, which decides the kinds of question it may
 * ask; an internal one is the only sensible answer for a survey of the people who were there. Who it
 * is meant for is a second call. It stays a draft until somebody publishes it, and that is left to
 * the person, because a survey with no questions in it is not one anybody should be sent.
 */
const {running: creatingSurvey, error: surveyError, run: runCreateSurvey} = useAsyncAction(
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
    {formatError: () => t('signupLists.createError')},
)

/**
 * What the dialog needs before it can offer anything: the templates it must pick one of, and
 * whatever was already prepared for this same evening.
 */
const {running: loadingProcedure, run: runLoadProcedure} = useAsyncAction(
    async () => {
      const [templates, prepared] = await Promise.all([
        procedures.getTemplates(),
        procedures.getProceduresForEvent(props.event.id, props.effectiveDate!),
      ])
      procedureTemplates.value = templates.filter(template => !template.archived)
      existingProcedure.value = prepared[0] ?? null
    },
    {formatError: () => t('signupLists.createError')},
)

async function openProcedure() {
  procedureTemplates.value = []
  existingProcedure.value = null
  showProcedure.value = true
  await runLoadProcedure()
}

function openExistingProcedure(procedure: Procedure) {
  showProcedure.value = false
  return router.push({name: 'procedure-detail', params: {id: procedure.id}})
}

/**
 * Writes the preparation list and hands it to the people who hold a place.
 *
 * <p>Two things are set that the ordinary create form leaves alone, and without either of them the
 * people on the list can look at it and do nothing else. The list itself is public, because a
 * private one is closed to an assignee who may not read every procedure of the station. Its steps
 * are marked as assigned to the user, because that is the mark a non-manager is allowed to tick.
 */
const {running: creatingProcedure, error: procedureError, run: runCreateProcedure} = useAsyncAction(
    async (payload: {templateId: number; name: string; description: string; dueAt: string}) => {
      const created = await procedures.createProcedure({
        templateId: payload.templateId,
        name: payload.name,
        description: payload.description || undefined,
        dueAt: dateToInstant(payload.dueAt) ?? undefined,
        isPublic: true,
        assigneeIds: props.memberSet.memberIds,
        eventId: props.event.id,
        eventDate: props.effectiveDate ?? undefined,
      })
      const detail = await procedures.getProcedure(created.id)
      for (const item of detail.items) {
        if (item.isPublic && item.userAssigned) continue
        await procedures.editItem(created.id, item.id, {
          title: item.title,
          description: item.description ?? undefined,
          isPublic: true,
          userAssigned: true,
          position: item.position,
        })
      }
      showProcedure.value = false
      showToast(t('signupLists.procedureCreated', {count: props.memberSet.count}), 'success')
      await router.push({name: 'procedure-detail', params: {id: created.id}})
    },
    {formatError: () => t('signupLists.createError')},
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
      :error="error"
      :member-set="memberSet"
      :date-label="dateLabel"
      :suggested-name="suggestedName"
      @submit="runCreateChecklist"
  />

  <SignupSurveyDialog
      v-model="showSurvey"
      :creating="creatingSurvey"
      :error="surveyError"
      :member-set="memberSet"
      :date-label="dateLabel"
      :suggested-name="suggestedName"
      @submit="runCreateSurvey"
  />

  <SignupProcedureDialog
      v-model="showProcedure"
      :loading="loadingProcedure"
      :creating="creatingProcedure"
      :error="procedureError"
      :member-set="memberSet"
      :date-label="dateLabel"
      :suggested-name="suggestedName"
      :occurrence-date="effectiveDate"
      :templates="procedureTemplates"
      :existing="existingProcedure"
      @submit="runCreateProcedure"
      @open="openExistingProcedure"
  />
</template>
