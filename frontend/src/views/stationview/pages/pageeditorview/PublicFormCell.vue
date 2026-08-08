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
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import EmptyHint from '@/components/typography/EmptyHint.vue'
import MutedText from '@/components/typography/MutedText.vue'
import PublicConsentCheckbox from '@/components/public/PublicConsentCheckbox.vue'
import {QuestionTypes} from '@/api/forms'
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

            <template v-if="!submitted">
                <div v-for="q in form.questions" :key="q.id" class="space-y-2">
                    <div>
                        <span class="font-medium text-sm">{{ q.title }}</span>
                        <span v-if="q.required" class="ml-1 text-error">*</span>
                        <MutedText v-if="q.description" tag="p" class="mt-0.5">{{ q.description }}</MutedText>
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
                                    @update:model-value="(v: string | number | null | undefined) => toggleChoice(q, Number(v))">
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

        <EmptyHint v-else-if="!loading">{{ t('publicForm.notFound') }}</EmptyHint>
    </div>
</template>
