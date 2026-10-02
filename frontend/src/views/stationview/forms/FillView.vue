/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import FormDraftNote from '@/components/forms/fill/FormDraftNote.vue'
import { useServerDraft } from './fillview/useServerDraft'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import { useAsyncAction } from '@/composables/useAsyncAction'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import InfoContainer from '@/components/container/InfoContainer.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import FormSentNotice from '@/components/forms/fill/FormSentNotice.vue'
import FillPages from './fillview/FillPages.vue'
import type {EligibleMembers, Form, FormAnswerValue, FormPage, FormQuestion} from '@/api/generated/schema'
import { forms } from '@/api'
import { emptyAnswer, storedAnswer } from '@/util/formAnswers'
import { presentQuestions } from '@/util/formShuffle'
import { useFormWalk } from '@/composables/useFormWalk'
import { useAnswerBaseline } from '@/composables/useAnswerBaseline'
import { describeFailure, type Failure } from '@/util/failure'
import { useSession } from '@/composables/useSession'
import { useSidebarCounts } from '@/composables/useSidebarCounts'
import MemberSelector from './fillview/MemberSelector.vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const { sessionInfo, loaded } = useSession()
const { refresh: refreshSidebarCounts } = useSidebarCounts()

const formId = computed(() => Number(route.params.id))
const form = ref<Form | null>(null)

/**
 * Which form is being answered, with the part after its name, because the same form also has a page
 * where it is written and the two tabs would otherwise read the same.
 */
const pageTitle = computed(() => form.value
    ? t('pages.forms-fill.titleNamed', {name: form.value.title})
    : t('pages.forms-fill.title'))

const pages = ref<FormPage[]>([])
const questions = ref<FormQuestion[]>([])
const answers = ref<Record<number, FormAnswerValue>>({})
const hasExistingResponse = ref(false)

const walk = useFormWalk(pages, questions, answers)

const selectedMemberId = ref<number | null>(null)
const eligibility = ref<EligibleMembers | null>(null)
const managedMembers = computed(() => sessionInfo.value?.managedMembers ?? [])

const canFillForSelf = computed(() => eligibility.value?.selfEligible ?? false)

const eligibleManagedMembers = computed(() => {
  if (!eligibility.value) return []
  const eligibleIds = new Set(eligibility.value.eligibleManagedMemberIds)
  return managedMembers.value.filter(m => eligibleIds.has(m.id))
})

const showMemberSelector = computed(() => {
  const targets = (canFillForSelf.value ? 1 : 0) + eligibleManagedMembers.value.length
  return targets > 1
})

const showSingleManagedHint = computed(() => {
  return !canFillForSelf.value && eligibleManagedMembers.value.length === 1
})

const singleManagedName = computed(() => {
  const member = eligibleManagedMembers.value[0]
  return member?.name || member?.email
})

const fillTargetOptions = computed(() => {
  const options: { id: number | null; label: string }[] = []
  if (canFillForSelf.value) {
    const name = sessionInfo.value?.account
        ? [sessionInfo.value.account.firstName, sessionInfo.value.account.lastName].filter(Boolean).join(' ')
        : ''
    options.push({ id: null, label: t('forms.fillForSelf', { name: name || t('forms.fillForSelfDefault') }) })
  }
  for (const m of eligibleManagedMembers.value) {
    options.push({ id: m.id, label: m.name || m.email || `#${m.id}` })
  }
  return options
})

const effectiveMemberId = computed(() => selectedMemberId.value)

const draft = useServerDraft(formId, effectiveMemberId, questions, answers, walk)
const baseline = useAnswerBaseline(answers, walk.path)

function initAnswerDefaults() {
  for (const q of questions.value) answers.value[q.id] = emptyAnswer(q.formQuestionType, q.config)
}

/**
 * Whether the answer already on file could be read, and why not where it could not.
 *
 * <p>This used to be swallowed and the form started blank, which is the worst of both: the reader
 * cannot see that their earlier answer is still there, and sending this one is treated as a first
 * answer rather than a correction. Saying so is the difference between a reader who reloads and one
 * who overwrites their own work.
 */
const priorAnswerFailure = ref<Failure | null>(null)

/**
 * Opens the answer already given by whoever the form is being filled for: the reader themselves or a
 * member in their care. An answer that arrives after the reader has switched to somebody else is
 * dropped, so one member's answers never land in another member's form.
 */
async function loadExistingResponse() {
  hasExistingResponse.value = false
  answers.value = {}
  priorAnswerFailure.value = null
  walk.restart()

  const memberId = effectiveMemberId.value
  try {
    const response = memberId
        ? await forms.getMemberResponse(formId.value, memberId)
        : await forms.getMyResponse(formId.value)
    if (memberId !== effectiveMemberId.value) return
    if (response.response) {
      hasExistingResponse.value = true
      initAnswerDefaults()
      for (const answer of response.answers) {
        const question = questions.value.find(q => q.id === answer.questionId)
        if (question) answers.value[question.id] = storedAnswer(question.formQuestionType, question.config, answer.value)
      }
    } else {
      initAnswerDefaults()
    }
  } catch (e) {
    if (memberId !== effectiveMemberId.value) return
    priorAnswerFailure.value = {...describeFailure(e, t), message: t('forms.priorAnswerUnknown')}
    initAnswerDefaults()
  }
  if (memberId !== effectiveMemberId.value) return
  await draft.resume()
  if (memberId === effectiveMemberId.value) baseline.settle()
}

/** Throws the kept draft away and opens the form the way it stood before it. */
async function startOver() {
  await draft.discard()
  await loadExistingResponse()
}

/** Goes on to the next page and keeps what is filled in so far, once anything was. */
function next() {
  if (walk.next() && baseline.changed()) void draft.keep()
}

onBeforeRouteLeave(() => {
  if (!sent.value && form.value && baseline.changed()) void draft.keep()
})

const { loading, failure, reload } = useAsyncLoader(async () => {
  const [f, formPages, qs, elig] = await Promise.all([
    forms.getForm(formId.value),
    forms.getPages(formId.value),
    forms.getQuestions(formId.value),
    forms.getEligibleMembers(formId.value),
  ])
  eligibility.value = elig
  form.value = f
  questions.value = presentQuestions(qs, f.shuffleQuestions)
  pages.value = formPages

  const firstManaged = eligibleManagedMembers.value[0]
  if (canFillForSelf.value) {
    selectedMemberId.value = null
  } else if (firstManaged) {
    selectedMemberId.value = firstManaged.id
  }

  await loadExistingResponse()
}, { autoLoad: false })
loading.value = true

watch(selectedMemberId, async () => {
  if (!loading.value) {
    await loadExistingResponse()
  }
})

const {failure: submitFailure, run: send} = useAsyncAction(async () => {
  try {
    await draft.settled()
    await sendAnswers(answers.value)
  } catch (e) {
    walk.showRefused(e)
    throw e
  }
  refreshSidebarCounts()
  sent.value = true
})

/**
 * The one failure to show. An answer that could not be sent is what the reader was last doing, so it
 * wins over a form that would not load: the second is a reason to reload the page, the first is a
 * reason to look at what they typed, and being told the wrong one costs them the answer.
 */
const displayFailure = computed(() => submitFailure.value ?? failure.value ?? priorAnswerFailure.value)

/** Sends the answers as a first answer or a correction, for the reader or the member in their care. */
async function sendAnswers(answerMap: Record<number, FormAnswerValue>) {
  const memberId = effectiveMemberId.value
  const data = { answers: answerMap }
  if (memberId) {
    await (hasExistingResponse.value
      ? forms.updateForMember(formId.value, memberId, data)
      : forms.submitForMember(formId.value, memberId, data))
  } else {
    await (hasExistingResponse.value ? forms.updateResponse(formId.value, data) : forms.submitResponse(formId.value, data))
  }
}

/** Whether the answer went through, which leaves the member on a screen saying so. */
const sent = ref(false)

/** Opens the answer just sent again, to correct it. */
async function changeAnswer() {
  sent.value = false
  await loadExistingResponse()
}

/** Sends the form once the page it is sent from is complete. */
function submit() {
  if (walk.checkCurrent()) void send()
}

onMounted(() => {
  if (loaded.value) reload()
})

watch(loaded, (isLoaded) => {
  if (isLoaded) reload()
})
</script>

<template>
  <ViewContent
      :title="pageTitle"
      :subtitle="t('pages.forms-fill.subtitle')"
  >
    <div class="space-y-6 max-w-3xl">
      <Spinner v-if="loading" size="lg" />
      <FailureAlert :failure="displayFailure"/>

      <template v-if="!loading && form">
        <div>
          <p v-if="form.description" class="text-(--text-muted) mt-1">{{ form.description }}</p>
        </div>

        <MemberSelector v-if="showMemberSelector"
                        v-model="selectedMemberId"
                        :options="fillTargetOptions" />

        <InfoContainer v-if="showSingleManagedHint">
          <p class="text-sm">
            {{ t('forms.fillForSingleManaged', { name: singleManagedName }) }}
          </p>
        </InfoContainer>

        <FormSentNotice v-if="sent" :message="form.completionMessage" :link="form.completionLink"
                        :link-label="form.completionLinkLabel">
          <ButtonRow>
            <SecondaryButton :icon="['fas', 'chevron-left']" @click="router.push({ name: 'forms-list' })">
              {{ t('forms.fill.backToForms') }}
            </SecondaryButton>
            <SecondaryButton v-if="form.allowEdit" :icon="['fas', 'pen']" data-testid="form-change-answer" @click="changeAnswer">
              {{ t('forms.fill.changeAnswer') }}
            </SecondaryButton>
          </ButtonRow>
        </FormSentNotice>

        <FormDraftNote v-if="!sent && draft.resumedFrom.value" :saved-at="draft.resumedFrom.value" @start-over="startOver"/>

        <FillPages v-if="!sent" v-model:answers="answers" :walk="walk"
                   :send-label="hasExistingResponse ? t('forms.update') : t('forms.submit')"
                   @send="submit" @next="next" @cancel="router.push({ name: 'forms-list' })"/>
      </template>
    </div>
  </ViewContent>
</template>
