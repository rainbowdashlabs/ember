/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import PublicConsentCheckbox from '@/components/public/PublicConsentCheckbox.vue'
import PublicFormQuestionCard from './PublicFormQuestionCard.vue'
import type {PublicForm, PublicFormQuestion} from '@/api/publicForms'

const {t} = useI18n()

defineProps<{
  form: PublicForm
  answers: Record<number, Record<string, unknown>>
  submitting: boolean
}>()

const emit = defineEmits<{
  (e: 'update-text', question: PublicFormQuestion, text: string): void
  (e: 'update-date', question: PublicFormQuestion, date: string): void
  (e: 'toggle-choice', question: PublicFormQuestion, optionIndex: number): void
  (e: 'submit'): void
}>()

const consentAccepted = defineModel<boolean>('consentAccepted', {required: true})
const consentVersion = defineModel<string>('consentVersion', {required: true})
const privacyVersion = defineModel<string>('privacyVersion', {required: true})
const tosVersion = defineModel<string>('tosVersion', {required: true})
</script>

<template>
  <div>
    <SectionHeader>{{ form.title }}</SectionHeader>
    <p v-if="form.description" class="mt-1 text-(--text-muted)">{{ form.description }}</p>
  </div>

  <div class="space-y-4">
    <PublicFormQuestionCard
        v-for="q in form.questions"
        :key="q.id"
        :question="q"
        :answer="answers[q.id] ?? {}"
        @update:text="(v: string) => emit('update-text', q, v)"
        @update:date="(v: string) => emit('update-date', q, v)"
        @toggle-choice="(oi: number) => emit('toggle-choice', q, oi)"/>
  </div>

  <NeutralContainer>
    <PublicConsentCheckbox
        v-model:accepted="consentAccepted"
        v-model:consent-version="consentVersion"
        v-model:privacy-version="privacyVersion"
        v-model:tos-version="tosVersion"/>
  </NeutralContainer>

  <div class="flex justify-end">
    <PrimaryButton :disabled="submitting || !consentAccepted" @click="emit('submit')">
      {{ submitting ? t('publicForm.submitting') : t('publicForm.submit') }}
    </PrimaryButton>
  </div>
</template>
