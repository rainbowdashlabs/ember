/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import { useAsyncAction } from '@/composables/useAsyncAction'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import InfoContainer from '@/components/container/InfoContainer.vue'
import {QuestionTypes, type EligibleMembers, type Form, type FormQuestion} from '@/api/forms'
import { forms } from '@/api'
import { describeFailure, type Failure } from '@/util/failure'
import { useSession } from '@/composables/useSession'
import { useSidebarCounts } from '@/composables/useSidebarCounts'
import MemberSelector from './fillview/MemberSelector.vue'
import QuestionCard from './fillview/QuestionCard.vue'

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

const questions = ref<FormQuestion[]>([])
const answers = ref<Record<number, Record<string, unknown>>>({})
const hasExistingResponse = ref(false)

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

function parseConfig(config: Record<string, unknown> | string): Record<string, unknown> {
  if (typeof config === 'object' && config !== null) return config
  try { return JSON.parse(config || '{}') } catch { return {} }
}

function initAnswerDefaults() {
  for (const q of questions.value) {
    if (q.formQuestionType === QuestionTypes.CHOICE) answers.value[q.id] = { selected: [], other: '' }
    else if (q.formQuestionType === QuestionTypes.TEXT) answers.value[q.id] = { text: '' }
    else if (q.formQuestionType === QuestionTypes.RATING) answers.value[q.id] = { rating: 0 }
    else if (q.formQuestionType === QuestionTypes.DATE) answers.value[q.id] = { date: '' }
    else if (q.formQuestionType === QuestionTypes.RANKING) {
      const cfg = parseConfig(q.config)
      const opts = (cfg.options as string[]) || []
      answers.value[q.id] = { order: opts.map((_: string, i: number) => i) }
    }
    else if (q.formQuestionType === QuestionTypes.LIKERT) answers.value[q.id] = { ratings: {} }
  }
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

async function loadExistingResponse() {
  hasExistingResponse.value = false
  answers.value = {}
  priorAnswerFailure.value = null

  try {
    let response
    if (effectiveMemberId.value) {
      initAnswerDefaults()
      return
    }
    response = await forms.getMyResponse(formId.value)
    if (response.response) {
      hasExistingResponse.value = true
      initAnswerDefaults()
      for (const answer of response.answers) {
        try {
          answers.value[answer.questionId] = JSON.parse(answer.value)
        } catch {
          answers.value[answer.questionId] = {}
        }
      }
    } else {
      initAnswerDefaults()
    }
  } catch (e) {
    priorAnswerFailure.value = {...describeFailure(e, t), message: t('forms.priorAnswerUnknown')}
    initAnswerDefaults()
  }
}

const { loading, failure, reload } = useAsyncLoader(async () => {
  const [f, qs, elig] = await Promise.all([
    forms.getForm(formId.value),
    forms.getQuestions(formId.value),
    forms.getEligibleMembers(formId.value),
  ])
  eligibility.value = elig
  form.value = f
  questions.value = qs

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

const {failure: submitFailure, run: submit} = useAsyncAction(async () => {
  const answerMap: Record<number, Record<string, unknown>> = {}
  for (const q of questions.value) {
    const value = answers.value[q.id]
    if (value === undefined) continue
    const type = q.formQuestionType
    answerMap[q.id] = { type, ...value }
  }

  if (effectiveMemberId.value) {
    if (hasExistingResponse.value) {
      await forms.updateForMember(formId.value, effectiveMemberId.value, { answers: answerMap })
    } else {
      await forms.submitForMember(formId.value, effectiveMemberId.value, { answers: answerMap })
    }
  } else {
    if (hasExistingResponse.value) {
      await forms.updateResponse(formId.value, { answers: answerMap })
    } else {
      await forms.submitResponse(formId.value, { answers: answerMap })
    }
  }
  refreshSidebarCounts()
  router.push({ name: 'forms-list' })
})

/**
 * The one failure to show. An answer that could not be sent is what the reader was last doing, so it
 * wins over a form that would not load: the second is a reason to reload the page, the first is a
 * reason to look at what they typed, and being told the wrong one costs them the answer.
 */
const displayFailure = computed(() => submitFailure.value ?? failure.value ?? priorAnswerFailure.value)

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

        <div class="space-y-4">
          <QuestionCard v-for="q in questions" :key="q.id"
                        v-model="answers[q.id]"
                        :question="q" />
        </div>

        <ButtonRow pair align="end">
          <SecondaryButton @click="router.push({ name: 'forms-list' })">{{ t('common.cancel') }}</SecondaryButton>
          <PrimaryButton @click="submit">
            {{ hasExistingResponse ? t('forms.update') : t('forms.submit') }}
          </PrimaryButton>
        </ButtonRow>
      </template>
    </div>
  </ViewContent>
</template>
