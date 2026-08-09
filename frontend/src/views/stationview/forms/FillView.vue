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
import Alert from '@/components/feedback/Alert.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import InfoContainer from '@/components/container/InfoContainer.vue'
import {QuestionTypes, type EligibleMembers, type Form, type FormQuestion} from '@/api/forms'
import { forms } from '@/api'
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

async function loadExistingResponse() {
  hasExistingResponse.value = false
  answers.value = {}

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
  } catch {
    initAnswerDefaults()
  }
}

const { loading, error, reload } = useAsyncLoader(async () => {
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

const {error: submitError, run: submit} = useAsyncAction(async () => {
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
}, {formatError: () => t('common.error')})

const displayError = computed(() => error.value || submitError.value)

onMounted(() => {
  if (loaded.value) reload()
})

watch(loaded, (isLoaded) => {
  if (isLoaded) reload()
})
</script>

<template>
  <ViewContent
      :title="t('pages.forms-fill.title')"
      :subtitle="t('pages.forms-fill.subtitle')"
  >
    <div class="space-y-6 max-w-3xl">
      <Spinner v-if="loading" size="lg" />
      <Alert v-if="displayError" variant="error">{{ displayError }}</Alert>

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

        <div class="flex justify-end gap-3">
          <SecondaryButton @click="router.push({ name: 'forms-list' })">{{ t('common.cancel') }}</SecondaryButton>
          <PrimaryButton @click="submit">
            {{ hasExistingResponse ? t('forms.update') : t('forms.submit') }}
          </PrimaryButton>
        </div>
      </template>
    </div>
  </ViewContent>
</template>
