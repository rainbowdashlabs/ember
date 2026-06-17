/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Alert from '@/components/feedback/Alert.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import EmptyHint from '@/components/typography/EmptyHint.vue'
import {publicForms} from '@/api'
import type {PublicForm, PublicFormQuestion} from '@/api/publicForms'
import {QuestionTypes} from '@/api/types'

const props = defineProps<{
  stationUid: string | null
  formPublicUid: string | null
  /** Drives the title fallback when no form is picked: POLL vs CONTACT. */
  variant: 'poll' | 'contact'
  /** Overrides shown above the form (FORMS_CTA only). */
  headlineOverride?: string | null
  bodyOverride?: string | null
}>()

const {t} = useI18n()

const loading = ref(false)
const submitting = ref(false)
const submitted = ref(false)
const error = ref('')
const form = ref<PublicForm | null>(null)
const answers = ref<Record<number, Record<string, unknown>>>({})

function initAnswerDefaults(questions: PublicFormQuestion[]) {
  answers.value = {}
  for (const q of questions) {
    if (q.questionType === QuestionTypes.CHOICE) answers.value[q.id] = {selected: [] as number[], other: ''}
    else if (q.questionType === QuestionTypes.TEXT) answers.value[q.id] = {text: ''}
    else if (q.questionType === QuestionTypes.DATE) answers.value[q.id] = {date: ''}
    else answers.value[q.id] = {}
  }
}

async function load() {
  if (!props.stationUid || !props.formPublicUid) {
    form.value = null
    return
  }
  loading.value = true
  error.value = ''
  submitted.value = false
  try {
    const data = await publicForms.getPublicForm(props.stationUid, props.formPublicUid)
    form.value = data
    initAnswerDefaults(data.questions)
  } catch {
    form.value = null
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
  if (!form.value || !props.stationUid || !props.formPublicUid) return
  submitting.value = true
  error.value = ''
  try {
    const answerMap: Record<number, Record<string, unknown>> = {}
    for (const q of form.value.questions) {
      const value = answers.value[q.id]
      if (value === undefined) continue
      answerMap[q.id] = {type: q.questionType, ...value}
    }
    await publicForms.submitPublicResponse(props.stationUid, props.formPublicUid, {answers: answerMap})
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
watch(() => [props.stationUid, props.formPublicUid], load)
</script>

<template>
    <div class="rounded-theme border border-(--border) p-4 space-y-3"
         :class="variant === 'contact' ? 'bg-primary/5' : 'bg-secondary/5'">
        <EmptyHint v-if="!formPublicUid">
            {{ variant === 'poll' ? t('publicForm.cellPollUnpicked') : t('publicForm.cellContactUnpicked') }}
        </EmptyHint>

        <template v-else-if="form">
            <div v-if="variant === 'contact' && (headlineOverride || bodyOverride)" class="space-y-1">
                <p v-if="headlineOverride" class="text-xl font-bold">{{ headlineOverride }}</p>
                <p v-if="bodyOverride" class="text-sm whitespace-pre-line">{{ bodyOverride }}</p>
            </div>
            <div v-else>
                <p class="text-lg font-semibold">{{ form.title }}</p>
                <p v-if="form.description" class="text-sm text-(--text-muted)">{{ form.description }}</p>
            </div>

            <Alert v-if="error" variant="error">{{ error }}</Alert>

            <template v-if="!submitted">
                <div v-for="q in form.questions" :key="q.id" class="space-y-2">
                    <div>
                        <span class="font-medium text-sm">{{ q.title }}</span>
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
                                     class="flex cursor-pointer items-center gap-2 rounded px-3 py-2 text-sm transition-colors"
                                     :class="(answers[q.id] as { selected: number[] })?.selected?.includes(oi)
                                         ? 'border border-primary bg-primary/10 text-primary'
                                         : 'border border-(--border) hover:border-primary/50'"
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

                <PrimaryButton :disabled="submitting" @click="submit">
                    {{ submitting ? t('publicForm.submitting') : t('publicForm.submit') }}
                </PrimaryButton>
            </template>

            <p v-else class="text-success text-sm">{{ t('publicForm.thanksText') }}</p>
        </template>

        <EmptyHint v-else-if="!loading">{{ t('publicForm.notFound') }}</EmptyHint>
    </div>
</template>
