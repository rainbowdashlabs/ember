/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import type {CorrectionResponse} from '@/api/generated/schema'
import {formatDateTime} from '@/util/format'

/**
 * Asking a request's signatures anew after a change: the documents generated for the same member since, of
 * which the manager picks the corrected one. The newest comes first and is picked to begin with.
 */
const props = defineProps<{
  corrections: CorrectionResponse[]
  /** Whether an action on the request is under way. */
  busy: boolean
}>()

const emit = defineEmits<{
  rectify: [generationId: number]
}>()

const {t} = useI18n()

const chosen = ref<number | null>(null)

watch(() => props.corrections, corrections => {
  chosen.value = corrections[0]?.generationId ?? null
}, {immediate: true})

function rectify() {
  if (chosen.value !== null) emit('rectify', Number(chosen.value))
}
</script>

<template>
  <div class="space-y-2" data-testid="signature-correction">
    <SubHeader>{{ t('signing.manage.correction.heading') }}</SubHeader>
    <MutedText size="sm" tag="p">{{ t('signing.manage.correction.hint') }}</MutedText>
    <FieldLabel for="signature-correction-choice">{{ t('signing.manage.correction.label') }}</FieldLabel>
    <SelectInput id="signature-correction-choice" v-model="chosen" class="w-full">
      <option v-for="correction in props.corrections" :key="correction.generationId" :value="correction.generationId">
        {{ t('signing.manage.correction.option', {template: correction.templateName, time: formatDateTime(correction.generatedAt)}) }}
      </option>
    </SelectInput>
    <SecondaryButton :icon="['fas', 'file-signature']" :disabled="props.busy || chosen === null"
                     data-testid="signature-correction-ask" @click="rectify">
      {{ t('signing.manage.correction.ask') }}
    </SecondaryButton>
  </div>
</template>
