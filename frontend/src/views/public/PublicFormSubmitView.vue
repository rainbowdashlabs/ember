/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SuccessContainer from '@/components/container/SuccessContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import PublicConsentCheckbox from '@/components/public/PublicConsentCheckbox.vue'
import {publicForms} from '@/api'
import type {PublicForm, PublicFormQuestion} from '@/api/publicForms'
import {QuestionTypes} from '@/api/types'

const {t} = useI18n()
const route = useRoute()

const stationUid = computed(() => String(route.params.stationUid))
const publicUid = computed(() => String(route.params.publicUid))

const loading = ref(true)
const submitting = ref(false)
const error = ref('')
const submitted = ref(false)
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

async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = await publicForms.getPublicForm(stationUid.value, publicUid.value)
    form.value = data
    initAnswerDefaults(data.questions)
  } catch {
    error.value = t('publicForm.notFound')
  } finally {
    loading.value = false
  }
}

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

async function submit() {
  if (!form.value) return
  if (!consentAccepted.value) {
    error.value = t('publicConsent.required')
    return
  }
  submitting.value = true
  error.value = ''
  try {
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
  } catch (e: unknown) {
    const status = (e as {response?: {status?: number}}).response?.status
    if (status === 409) error.value = t('publicForm.alreadyAnswered')
    else if (status === 429) error.value = t('publicForm.rateLimited')
    else error.value = t('publicForm.submitError')
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <ViewContent>
    <div class="space-y-6 max-w-3xl">
      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <SuccessContainer v-if="submitted">
        <SectionHeader>{{ t('publicForm.thanksTitle') }}</SectionHeader>
        <p class="mt-2 text-sm">{{ t('publicForm.thanksText') }}</p>
      </SuccessContainer>

      <template v-if="!loading && form && !submitted">
        <div>
          <SectionHeader>{{ form.title }}</SectionHeader>
          <p v-if="form.description" class="mt-1 text-(--text-muted)">{{ form.description }}</p>
        </div>

        <div class="space-y-4">
          <NeutralContainer v-for="q in form.questions" :key="q.id">
            <div class="space-y-3">
              <div>
                <span class="font-medium">{{ q.title }}</span>
                <span v-if="q.required" class="ml-1 text-error">*</span>
                <p v-if="q.description" class="mt-0.5 text-xs text-(--text-muted)">{{ q.description }}</p>
              </div>

              <template v-if="q.questionType === QuestionTypes.TEXT">
                <TextAreaInput v-if="q.config.longAnswer"
                               v-model="(answers[q.id] as { text: string }).text"/>
                <TextInput v-else v-model="(answers[q.id] as { text: string }).text"/>
              </template>

              <template v-if="q.questionType === QuestionTypes.DATE">
                <DateInput v-model="(answers[q.id] as { date: string }).date"/>
              </template>

              <template v-if="q.questionType === QuestionTypes.CHOICE">
                <div class="space-y-1">
                  <template v-if="q.config.dropdown">
                    <SelectInput
                        :model-value="String((answers[q.id] as { selected: number[] })?.selected?.[0] ?? '')"
                        @update:model-value="(v: string | undefined) => toggleChoice(q, Number(v))">
                      <option value="">--</option>
                      <option v-for="(opt, oi) in (q.config.options as string[])" :key="oi" :value="oi">
                        {{ opt }}
                      </option>
                    </SelectInput>
                  </template>
                  <template v-else>
                    <div v-for="(opt, oi) in (q.config.options as string[])"
                         :key="oi"
                         class="flex cursor-pointer items-center gap-2 rounded-lg border-2 px-4 py-3 text-sm font-medium transition-all"
                         :class="(answers[q.id] as { selected: number[] })?.selected?.includes(oi)
                           ? 'border-primary bg-primary/10 text-primary'
                           : 'border-bg-light-accent dark:border-bg-dark-accent text-(--text) hover:border-primary/50'"
                         @click="toggleChoice(q, oi)">
                      <font-awesome-icon
                          :icon="['fas', (answers[q.id] as { selected: number[] })?.selected?.includes(oi)
                            ? (q.config.multiSelect ? 'square-check' : 'circle-dot')
                            : (q.config.multiSelect ? 'square' : 'circle')]"
                          :class="(answers[q.id] as { selected: number[] })?.selected?.includes(oi) ? 'text-primary' : 'text-(--text-muted)'"
                          class="shrink-0"/>
                      <span>{{ opt }}</span>
                    </div>
                  </template>
                </div>
              </template>
            </div>
          </NeutralContainer>
        </div>

        <NeutralContainer>
          <PublicConsentCheckbox
              v-model:accepted="consentAccepted"
              v-model:consent-version="consentVersion"
              v-model:privacy-version="privacyVersion"
              v-model:tos-version="tosVersion"/>
        </NeutralContainer>

        <div class="flex justify-end">
          <PrimaryButton :disabled="submitting || !consentAccepted" @click="submit">
            {{ submitting ? t('publicForm.submitting') : t('publicForm.submit') }}
          </PrimaryButton>
        </div>
      </template>
    </div>
  </ViewContent>
</template>
