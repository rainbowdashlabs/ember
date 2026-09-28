/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import { useInstantSave } from '@/composables/useInstantSave'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import InstantSaveNotice from '@/components/feedback/InstantSaveNotice.vue'
import FormShareLink from '@/components/public/FormShareLink.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import FormQuestionsSection from './builderview/FormQuestionsSection.vue'
import FormMetadataEditor from './builderview/FormMetadataEditor.vue'
import FormRestrictionsEditor from './builderview/FormRestrictionsEditor.vue'
import { type RestrictionSelection, emptyRestriction } from '@/components/input/restriction'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import { storedQuestionId } from './builderview/types'
import { useFormLayout } from './builderview/useFormLayout'
import { useUnsavedLayout } from './builderview/useUnsavedLayout'
import ContentDraftBanner from '@/components/content/ContentDraftBanner.vue'
import { AnswerLossDeclined, type Removals, useAnswerLossConsent } from './builderview/useAnswerLossConsent'
import {FormPurpose, FormVisibility, QUESTION_TYPES_BY_PURPOSE, type Form, type FormPurposeName, type FormQuestion, type FormVisibilityName, type PageUsingForm, type QuestionType} from '@/api/forms'
import { optionKeysOf } from '@/util/formOptions'
import type { MemberGroup, StationMember, UserTag } from '@/api/types'
import { forms, memberGroups, userTags, stationMembers } from '@/api'
import { describeFailure, type Failure } from '@/util/failure'
import { instantToLocalInput } from '@/util/format'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const formId = computed(() => route.params.id ? Number(route.params.id) : null)

/**
 * The form's purpose. Loaded from the form when editing, derived from the
 * `?purpose=…` query when creating, defaults to INTERNAL.
 */
const purpose = ref<FormPurposeName>(FormPurpose.INTERNAL)

/** Route name to return to after save/cancel, derived from the current form's purpose. */
const returnRouteName = computed(() => {
  if (purpose.value === FormPurpose.CONTACT) return 'pages-forms'
  if (purpose.value === FormPurpose.POLL) return 'pages-polls'
  return 'forms-list'
})

const title = ref('')
const description = ref('')
const shuffleQuestions = ref(false)
const allowEdit = ref(true)
const forced = ref(false)
const startAt = ref('')
const endAt = ref('')
const visibility = ref<FormVisibilityName>(FormVisibility.PUBLIC)

/**
 * The reach the form is stored with, which is what a change is measured against.
 *
 * <p>Not the previous value of the box: reading the form fills the box in, and a form already
 * stored as reachable by link alone moved it off the default the moment it loaded. Measured that
 * way, opening the editor counted as a change, wrote the reach back and minted a link nobody had
 * asked for.
 */
const storedVisibility = ref<FormVisibilityName | null>(null)

/**
 * The pages that still put this form on themselves after it was closed to its link alone.
 *
 * <p>Nothing stops the change: the editor may well mean it, and the cells are theirs to tidy. What
 * they cannot do is notice, because those pages go on rendering with a poll on them that nobody
 * outside can answer any more.
 */
const heldBy = ref<PageUsingForm[]>([])

/** The form as the server last gave it, for the panel that shows where it is reached. */
const loadedForm = ref<Form | null>(null)

/**
 * A form being edited is named after itself; one being created has no name yet. What stands here is
 * the stored title rather than the field being typed into, so the page header does not change under
 * the reader's hands.
 */
const pageTitle = computed(() => loadedForm.value?.title
    || t(formId.value ? 'pages.forms-edit.title' : 'pages.forms-create.title'))

const pageSubtitle = computed(() =>
    t(formId.value ? 'pages.forms-edit.subtitle' : 'pages.forms-create.subtitle'))

const allGroups = ref<MemberGroup[]>([])
const allTags = ref<UserTag[]>([])
const allMembers = ref<StationMember[]>([])
const restriction = ref<RestrictionSelection>(emptyRestriction())

/**
 * Whether the form is answered by somebody the station knows.
 *
 * <p>Narrowing who may answer needs somebody to narrow it to. A contact form and a poll on a public
 * page are answered by whoever opens the link, with no account behind the answer, so nothing
 * anywhere reads what the picker sets: it was a screenful of groups and tags that decided nothing.
 */
const answeredByMembers = computed(() => purpose.value === FormPurpose.INTERNAL)

const layout = useFormLayout()
const unsaved = useUnsavedLayout(() => `form-layout-${formId.value ?? `new-${purpose.value}`}`, layout.pages)

/** Leaves the editor after the reader agreed to leave the unsaved questions behind. */
function leaveUnsaved() {
  const to = unsaved.leaveAnyway()
  if (to) router.push(to)
}

/** A question as the server holds it, with the keys of its options, which a save measures its removals against. */
interface StoredQuestion {
  id: number
  optionKeys: string[]
}

const storedQuestions = ref<StoredQuestion[]>([])

function remember(stored: FormQuestion[]) {
  storedQuestions.value = stored.map(question => ({id: question.id, optionKeys: optionKeysOf(question.config)}))
}

const {
  show: askingAboutAnswerLoss,
  message: answerLossMessage,
  requireConsent: requireAnswerLossConsent,
  accept: acceptAnswerLoss,
} = useAnswerLossConsent()

/** The stored questions the save removes, and the options it removes from the questions it keeps. */
function removals(): Removals {
  const drafts = new Map(layout.allQuestions.value.map(draft => [storedQuestionId(draft), draft]))
  const questionIds = storedQuestions.value.filter(stored => !drafts.has(stored.id)).map(stored => stored.id)
  const options = storedQuestions.value.flatMap(stored => {
    const draft = drafts.get(stored.id)
    if (!draft) return []
    const kept = new Set(optionKeysOf(draft.config))
    const keys = stored.optionKeys.filter(key => !kept.has(key))
    return keys.length > 0 ? [{questionId: stored.id, keys}] : []
  })
  return {questionIds, options}
}

const questionTypes = computed<QuestionType[]>(() => QUESTION_TYPES_BY_PURPOSE[purpose.value])

const { loading, failure: loadFailure } = useAsyncLoader(async () => {
  const [groups, tags, members] = await Promise.all([
    memberGroups.listGroups(),
    userTags.listTags(),
    stationMembers.listMembers(),
  ])
  allGroups.value = groups
  allTags.value = tags
  allMembers.value = members

  if (!formId.value) {
    const queryPurpose = typeof route.query.purpose === 'string' ? route.query.purpose : null
    if (queryPurpose && queryPurpose in FormPurpose) {
      purpose.value = queryPurpose as FormPurposeName
    }
    unsaved.settle(true)
    return
  }

  const [form, pages, qs, restrictions] = await Promise.all([
    forms.getForm(formId.value),
    forms.getPages(formId.value),
    forms.getQuestions(formId.value),
    forms.getRestrictions(formId.value),
  ])
  loadedForm.value = form
  title.value = form.title
  description.value = form.description
  shuffleQuestions.value = form.shuffleQuestions
  allowEdit.value = form.allowEdit
  forced.value = form.forced ?? false
  startAt.value = instantToLocalInput(form.startAt)
  endAt.value = instantToLocalInput(form.endAt)
  purpose.value = form.purpose
  visibility.value = form.visibility
  storedVisibility.value = form.visibility

  restriction.value = {
    userTypes: restrictions.userTypes ?? [],
    groupIds: restrictions.groupIds ?? [],
    tagIds: restrictions.tagIds ?? [],
    memberIds: restrictions.memberIds ?? [],
    mode: 'AND',
  }

  remember(qs)
  layout.load({pages, questions: qs})
  unsaved.settle(true)

  settings.arm()
  if (answeredByMembers.value) limits.arm()
})

/**
 * The form's own settings, written back as they are changed.
 *
 * <p>Only the questions wait for the button. Everything else is a switch or a date that somebody
 * sets and considers set, and a form left reaching further than its editor believed, because they
 * changed a setting and walked away, is the accident worth designing out.
 *
 * <p>Armed only once a form has been read, and only where there is a form to write to: what is
 * being created has nowhere to go yet, so it is the button that brings it into being.
 */
const settings = useInstantSave(
    () => currentSettings(),
    async data => {
      if (formId.value) await forms.updateForm(formId.value, data)
    })

const limits = useInstantSave(
    () => ({...restriction.value}),
    async selection => {
      if (!formId.value || !answeredByMembers.value) return
      await forms.setRestrictions(formId.value, {
        userTypes: selection.userTypes,
        groupIds: selection.groupIds,
        tagIds: selection.tagIds,
        memberIds: selection.memberIds,
      })
    })

/** True while anything is on its way to the server, so the page can say so rather than look idle. */
const savingSettings = computed(() => settings.saving.value || limits.saving.value)
const settingsFailed = computed(() => settings.failed.value || limits.failed.value)

function currentSettings() {
  return {
    title: title.value,
    description: description.value,
    shuffleQuestions: shuffleQuestions.value,
    allowEdit: allowEdit.value,
    forced: forced.value,
    startAt: startAt.value ? new Date(startAt.value).toISOString() : null,
    endAt: endAt.value ? new Date(endAt.value).toISOString() : null,
    purpose: purpose.value,
  }
}

async function saveForm(): Promise<number> {
  const id = formId.value
  if (id) {
    await settings.flush()
    return id
  }
  const created = await forms.createForm(currentSettings())
  await saveVisibility(created.id)
  return created.id
}

/**
 * How far the form reaches is set on its own, because it is not part of the form's own settings: it
 * says who can get to the form rather than what the form asks. An internal form has no reach at all
 * and the server refuses to be told about one.
 */
async function saveVisibility(id: number) {
  if (purpose.value === FormPurpose.INTERNAL) return
  await forms.setFormVisibility(id, visibility.value)
}

/**
 * Turning a form to its link alone takes effect at once, and gives it a link where it has none.
 *
 * <p>Waiting for the save would mean showing an address that does not exist yet, or showing nothing
 * where somebody has just asked for a link. What the link opens still follows the last save, which
 * is what the note beside it says.
 *
 * <p>A form keeps the one link it was given. Opening it to everybody and closing it again leaves
 * that link alone, so a link already handed out still works afterwards; ending it is the button
 * that says so and nothing else.
 */
watch(visibility, async now => {
  const id = formId.value
  if (!id || !loadedForm.value || purpose.value === FormPurpose.INTERNAL) return
  if (now === storedVisibility.value) return
  try {
    const {form: saved, stillHeldBy} = await forms.setFormVisibility(id, now)
    storedVisibility.value = now
    if (now === FormVisibility.UNLISTED && !(await forms.getFormShareLink(id))) {
      await forms.replaceFormShareLink(id, null)
    }
    loadedForm.value = saved
    heldBy.value = stillHeldBy
  } catch (e) {
    actionFailure.value = {...describeFailure(e, t), message: t('forms.visibilityFailed')}
  }
})

/**
 * Sends the pages and questions, each stored question with its id so it keeps its answers, and takes
 * the ids the server gave the new ones. A second save after a later step failed then changes those
 * questions instead of adding them again.
 */
async function saveQuestions(id: number) {
  const stored = await forms.saveLayout(id, layout.toRequest())
  layout.adoptIds(stored)
  remember(stored.questions)
}

/**
 * What the reader's last action ran into, kept apart from what the page failed to load. A save that
 * was refused and a form that never arrived call for different things, and one line holding whichever
 * happened last told them neither.
 */
const actionFailure = ref<Failure | null>(null)

async function save() {
  actionFailure.value = null
  try {
    if (formId.value) await requireAnswerLossConsent(formId.value, removals())
    const id = await saveForm()
    await saveQuestions(id)
    await limits.flush()
    if (answeredByMembers.value && !formId.value) {
      await forms.setRestrictions(id, {
        userTypes: restriction.value.userTypes,
        groupIds: restriction.value.groupIds,
        tagIds: restriction.value.tagIds,
        memberIds: restriction.value.memberIds,
      })
    }
    unsaved.settle()
    router.push({ name: returnRouteName.value })
  } catch (e) {
    if (!(e instanceof AnswerLossDeclined)) actionFailure.value = describeFailure(e, t)
    throw e
  }
}
</script>

<template>
  <ViewContent
      :title="pageTitle"
      :subtitle="pageSubtitle"
  >
    <div class="space-y-6 max-w-3xl">
      <Spinner v-if="loading" size="lg" />
      <FailureAlert :failure="actionFailure ?? loadFailure"/>

      <template v-if="!loading">
        <InstantSaveNotice v-if="formId" :saving="savingSettings" :failed="settingsFailed"
            :label="t('forms.settingsSaved')" :saving-label="t('forms.settingsSaving')"
            :failed-label="t('forms.settingsSaveFailed')"/>

        <FormMetadataEditor
          v-model:title="title"
          v-model:description="description"
          v-model:start-at="startAt"
          v-model:end-at="endAt"
          v-model:shuffle-questions="shuffleQuestions"
          v-model:allow-edit="allowEdit"
          v-model:forced="forced"
          v-model:visibility="visibility"
          :purpose="purpose"
        />

        <FormShareLink v-if="loadedForm && purpose !== FormPurpose.INTERNAL" :form="loadedForm" unsaved/>

        <Alert v-if="heldBy.length > 0" variant="info">
          {{ t('forms.stillHeldBy', {pages: heldBy.map(p => p.title).join(', ')}) }}
        </Alert>

        <FormRestrictionsEditor
          v-if="answeredByMembers"
          :groups="allGroups"
          :tags="allTags"
          :members="allMembers"
          v-model="restriction"
        />

        <ContentDraftBanner v-if="unsaved.offered.value" :saved-at="unsaved.offered.value.savedAt"
                            @restore="unsaved.restore()" @discard="unsaved.discard()"/>
        <FormQuestionsSection :layout="layout" :question-types="questionTypes"/>

        <div class="flex justify-end gap-3">
          <SecondaryButton @click="router.push({ name: returnRouteName })">{{ t('common.cancel') }}</SecondaryButton>
          <SaveButton :action="save"/>
        </div>
      </template>
    </div>

    <ConfirmDeleteModal v-model="unsaved.askingToLeave.value"
        :message="t('forms.leaveUnsaved')"
        :confirm-label="t('forms.leaveAnyway')"
        @confirm="leaveUnsaved"/>

    <ConfirmDeleteModal v-model="askingAboutAnswerLoss"
        :message="answerLossMessage"
        :confirm-label="t('forms.saveAndDeleteAnswers')"
        @confirm="acceptAnswerLoss"/>
  </ViewContent>
</template>
