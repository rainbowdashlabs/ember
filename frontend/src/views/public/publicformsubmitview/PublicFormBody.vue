/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import PublicConsentCheckbox from '@/components/public/PublicConsentCheckbox.vue'
import PublicPageFields from '@/components/forms/fill/PublicPageFields.vue'
import FormPageNav from '@/components/forms/fill/FormPageNav.vue'
import PublicFormClosedNotice from '@/components/forms/fill/PublicFormClosedNotice.vue'
import type {PublicForm, PublicFormPage, PublicFormQuestion} from '@/api/publicForms'
import type {AnswerValue} from '@/util/formAnswers'
import type {useFormWalk} from '@/composables/useFormWalk'

const {t} = useI18n()

/**
 * A public form on a page of its own, one of its pages at a time. The consent boxes stand on the page
 * the form is sent from, above the button that sends it.
 */
defineProps<{
  form: PublicForm
  answers: Record<number, AnswerValue>
  walk: ReturnType<typeof useFormWalk<PublicFormPage, PublicFormQuestion>>
  submitting: boolean
  /** A form that is not taking answers shows why and offers nothing to fill in. */
  open: boolean
}>()

const emit = defineEmits<{
  (e: 'update-text', question: PublicFormQuestion, text: string): void
  (e: 'update-date', question: PublicFormQuestion, date: string): void
  (e: 'toggle-choice', question: PublicFormQuestion, optionKey: string): void
  (e: 'submit'): void
}>()

const consentAccepted = defineModel<boolean>('consentAccepted', {required: true})
const consentVersion = defineModel<string>('consentVersion', {required: true})
const privacyVersion = defineModel<string>('privacyVersion', {required: true})
const tosVersion = defineModel<string>('tosVersion', {required: true})
</script>

<template>
  <div>
    <SubHeader>{{ form.title }}</SubHeader>
    <p v-if="form.description" class="mt-1 text-(--text-muted)">{{ form.description }}</p>
  </div>

  <PublicFormClosedNotice v-if="!open" :state="form.state" :closed-since="form.closedSince"/>

  <template v-else>
    <PublicPageFields :walk="walk" :answers="answers" framed
                    @update-text="(q, v) => emit('update-text', q, v)"
                    @update-date="(q, v) => emit('update-date', q, v)"
                    @toggle-choice="(q, key) => emit('toggle-choice', q, key)"/>

    <NeutralContainer v-if="walk.isLast.value">
      <PublicConsentCheckbox
          v-model:accepted="consentAccepted"
          v-model:consent-version="consentVersion"
          v-model:privacy-version="privacyVersion"
          v-model:tos-version="tosVersion"/>
    </NeutralContainer>

    <FormPageNav :can-go-back="walk.pageNumber.value > 1" :is-last="walk.isLast.value"
                 :send-label="submitting ? t('publicForm.submitting') : t('publicForm.submit')"
                 :sending="submitting" :send-disabled="!consentAccepted"
                 @back="walk.back()" @next="walk.next()" @send="emit('submit')"/>
  </template>
</template>
