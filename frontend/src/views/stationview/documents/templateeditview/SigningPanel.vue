/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import LabelledField from '@/components/input/LabelledField.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import ToggleSetting from '@/components/input/toggle/ToggleSetting.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {TemplateSigning} from '@/api/generated/schema'
import {LEGAL_RETENTION_MONTHS, MAX_RETENTION_MONTHS, signingOf, type TemplateDraft} from './templateDraft'

/**
 * How the template's documents are kept and sent once they are signed. Who signs and what each signer
 * confirms stand at the signature lines of a letter and the signature fields of a PDF; this is what holds
 * for the whole template.
 *
 * <p>Signed documents, their evidence and the signed PDF are kept as long as the member is a member. They
 * may be kept for a number of months after the member has left, as evidence for claims; a legal template
 * starts at four years. The copy a signer gets by mail carries the checksum and a link, and the PDF itself
 * only where switched on here, since consent forms may hold health data.
 *
 * <p>A request for signatures copies both when it is made, so a change here only reaches documents asked
 * to be signed afterwards.
 */
const draft = defineModel<TemplateDraft>({required: true})

const {t} = useI18n()

const signing = computed(() => signingOf(draft.value))
const kept = computed(() => signing.value.retentionMonths !== null)

function set(change: Partial<TemplateSigning>) {
  draft.value.signing = {...signing.value, ...change}
}

function setKept(keep: boolean) {
  set({retentionMonths: keep ? LEGAL_RETENTION_MONTHS : null})
}

function setMonths(months: number | undefined) {
  const whole = Math.round(months ?? 0)
  set({retentionMonths: Math.min(Math.max(whole, 0), MAX_RETENTION_MONTHS)})
}
</script>

<template>
  <NeutralContainer class="space-y-4" data-testid="template-signing">
    <SubHeader>{{ t('documentTemplates.signing.title') }}</SubHeader>
    <MutedText size="sm" tag="p">{{ t('documentTemplates.signing.hint') }}</MutedText>
    <ToggleSetting :model-value="kept" :label="t('documentTemplates.signing.keep')"
                   :hint="t('documentTemplates.signing.keepHint')" data-testid="template-signing-keep"
                   @update:model-value="setKept"/>
    <LabelledField v-if="kept" :label="t('documentTemplates.signing.months')"
                   :help="t('documentTemplates.signing.monthsHelp', {max: MAX_RETENTION_MONTHS})">
      <NumberInput :model-value="signing.retentionMonths ?? 0" data-testid="template-signing-months"
                   @update:model-value="setMonths"/>
    </LabelledField>
    <ToggleSetting :model-value="signing.copyAttached" :label="t('documentTemplates.signing.copyAttached')"
                   :hint="t('documentTemplates.signing.copyAttachedHint')" data-testid="template-signing-copy"
                   @update:model-value="copyAttached => set({copyAttached})"/>
    <MutedText size="sm" tag="p">{{ t('documentTemplates.signing.frozen') }}</MutedText>
  </NeutralContainer>
</template>
