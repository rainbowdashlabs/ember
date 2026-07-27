/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import SuccessContainer from '@/components/container/SuccessContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import PublicFormBody from './publicformsubmitview/PublicFormBody.vue'
import {publicForms} from '@/api'
import type {PublicForm, PublicFormQuestion} from '@/api/publicForms'
import {QuestionTypes} from '@/api/types'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useAsyncAction} from '@/composables/useAsyncAction'

const {t} = useI18n()
const route = useRoute()

const stationUid = computed(() => String(route.params.stationUid))
const publicUid = computed(() => String(route.params.publicUid))

const submitted = ref(false)
const validationError = ref('')
const form = ref<PublicForm | null>(null)
const answers = ref<Record<number, Record<string, unknown>>>({})
const consentAccepted = ref(false)
const consentVersion = ref('')
const privacyVersion = ref('')
const tosVersion = ref('')

function initAnswerDefaults(questions: PublicFormQuestion[]) {
  for (const q of questions) {
    if (q.questionType === QuestionTypes.CHOICE) answers.value[q.id] = {selected: [] as number[], other: ''}
    else if (q.questionType === QuestionTypes.TEXT) answers.value[q.id] = {text: ''}
    else if (q.questionType === QuestionTypes.DATE) answers.value[q.id] = {date: ''}
    else answers.value[q.id] = {}
  }
}

const {loading, error: loadError} = useAsyncLoader(async () => {
  const data = await publicForms.getPublicForm(stationUid.value, publicUid.value)
  form.value = data
  initAnswerDefaults(data.questions)
}, {errorMessageKey: 'publicForm.notFound'})

function toggleChoice(q: PublicFormQuestion, optionIndex: number) {
  const ans = answers.value[q.id] as {selected: number[]; other: string}
  const multi = !!q.config.multiSelect
  if (multi) {
    const idx = ans.selected.indexOf(optionIndex)
    if (idx >= 0) ans.selected.splice(idx, 1)
    else ans.selected.push(optionIndex)
  } else {
    ans.selected = [optionIndex]
    ans.other = ''
  }
}

function updateText(q: PublicFormQuestion, text: string) {
  (answers.value[q.id] as {text: string}).text = text
}

function updateDate(q: PublicFormQuestion, date: string) {
  (answers.value[q.id] as {date: string}).date = date
}

const {running: submitting, error: submitError, run: runSubmit} = useAsyncAction(async () => {
  if (!form.value) return
  const answerMap: Record<number, Record<string, unknown>> = {}
  for (const q of form.value.questions) {
    const value = answers.value[q.id]
    if (value === undefined) continue
    answerMap[q.id] = {type: q.questionType, ...value}
  }
  await publicForms.submitPublicResponse(stationUid.value, publicUid.value, {
    answers: answerMap,
    consentVersion: consentVersion.value,
    privacyVersion: privacyVersion.value,
    tosVersion: tosVersion.value,
  })
  submitted.value = true
}, {formatError: (e) => {
  const status = (e as {response?: {status?: number}}).response?.status
  if (status === 409) return t('publicForm.alreadyAnswered')
  if (status === 429) return t('publicForm.rateLimited')
  return t('publicForm.submitError')
}})

const error = computed(() => loadError.value || validationError.value || submitError.value)

function submit() {
  if (!consentAccepted.value) {
    validationError.value = t('publicConsent.required')
    return
  }
  validationError.value = ''
  void runSubmit()
}
</script>

<template>
  <ViewContent :title="t('pages.public-form-submit.title')" :subtitle="t('pages.public-form-submit.subtitle')">
    <div class="space-y-6 max-w-3xl">
      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <SuccessContainer v-if="submitted">
        <SectionHeader>{{ t('publicForm.thanksTitle') }}</SectionHeader>
        <p class="mt-2 text-sm">{{ t('publicForm.thanksText') }}</p>
      </SuccessContainer>

      <PublicFormBody
          v-if="!loading && form && !submitted"
          :form="form"
          :answers="answers"
          v-model:consent-accepted="consentAccepted"
          v-model:consent-version="consentVersion"
          v-model:privacy-version="privacyVersion"
          v-model:tos-version="tosVersion"
          :submitting="submitting"
          @update-text="updateText"
          @update-date="updateDate"
          @toggle-choice="toggleChoice"
          @submit="submit"/>
    </div>
  </ViewContent>
</template>
