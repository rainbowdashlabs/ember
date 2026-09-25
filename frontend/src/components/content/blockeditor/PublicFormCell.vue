/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Alert from '@/components/feedback/Alert.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import EmptyHint from '@/components/typography/EmptyHint.vue'
import MutedText from '@/components/typography/MutedText.vue'
import PublicConsentCheckbox from '@/components/public/PublicConsentCheckbox.vue'
import PublicQuestionFields from '@/components/forms/fill/PublicQuestionFields.vue'
import PublicFormClosedNotice from '@/components/forms/fill/PublicFormClosedNotice.vue'
import {usePublicFormSubmission} from '@/composables/usePublicFormSubmission'

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

const {
  form,
  open,
  answers,
  loading,
  loadError: error,
  submitted,
  validationError,
  consentAccepted,
  consentVersion,
  privacyVersion,
  tosVersion,
  submitting,
  submitError,
  load,
  toggleChoice,
  updateText,
  updateDate,
  submit,
} = usePublicFormSubmission(
    computed(() => props.stationUid),
    computed(() => props.formPublicUid),
)

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
                <MutedText v-if="form.description" tag="p" size="sm">{{ form.description }}</MutedText>
            </div>

            <Alert v-if="error || submitError || validationError" variant="error">
                {{ error || submitError || validationError }}
            </Alert>

            <PublicFormClosedNotice v-if="!open" :state="form.state"/>

            <template v-else-if="!submitted">
                <div v-for="q in form.questions" :key="q.id" class="space-y-2">
                    <div>
                        <span class="font-medium text-sm">{{ q.title }}</span>
                        <span v-if="q.required" class="ml-1 text-error">*</span>
                        <MutedText v-if="q.description" tag="p" class="mt-0.5">{{ q.description }}</MutedText>
                    </div>

                    <PublicQuestionFields
                        :question="q"
                        :answer="answers[q.id]"
                        @update:text="(v: string) => updateText(q, v)"
                        @update:date="(v: string) => updateDate(q, v)"
                        @toggle-choice="(oi: number) => toggleChoice(q, oi)"/>
                </div>

                <PublicConsentCheckbox
                    v-model:accepted="consentAccepted"
                    v-model:consent-version="consentVersion"
                    v-model:privacy-version="privacyVersion"
                    v-model:tos-version="tosVersion"/>

                <PrimaryButton :disabled="submitting" @click="submit">
                    {{ submitting ? t('publicForm.submitting') : t('publicForm.submit') }}
                </PrimaryButton>
            </template>

            <p v-else class="text-success text-sm">{{ t('publicForm.thanksText') }}</p>
        </template>

        <Alert v-else-if="!loading" variant="error">{{ t('publicForm.unreachableHere') }}</Alert>
    </div>
</template>
