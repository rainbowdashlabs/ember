/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {FieldState} from '@/api/generated/schema'
import type {EvidenceSummary} from '@/api/generated/schema'
import {formatDateTime} from '@/util/format'
import {proofWords} from '@/components/documents/signatures/proofWords'

/**
 * The signatures a signed document records in the details it carries: who signed which field, when and
 * with what, and whether several fields were confirmed together; and where the station's issuer signed
 * when the document was made. They come from the file itself, so whether they can be trusted is what the
 * seal check above says.
 */
defineProps<{evidence: EvidenceSummary}>()

const {t} = useI18n()

function line(field: EvidenceSummary['fields'][number]): string {
  const role = t(`signing.ask.role.${field.role}`)
  if (field.state !== FieldState.SIGNED || !field.signerName || !field.signedAt || !field.proof) {
    return t('sealVerification.evidence.notSigned', {role, state: t(`signing.ask.state.${field.state}`)})
  }
  return t('sealVerification.evidence.signed', {
    role,
    name: field.signerName,
    time: formatDateTime(field.signedAt),
    proof: proofWords(field.proof, t),
  })
}
</script>

<template>
  <NeutralContainer class="space-y-2" data-testid="seal-evidence">
    <SubHeader>{{ t('sealVerification.evidence.title') }}</SubHeader>
    <MutedText tag="p" size="sm">{{ t('sealVerification.evidence.hint') }}</MutedText>
    <ul class="list-disc pl-5 space-y-1 text-sm">
      <li v-for="field in evidence.fields" :key="field.fieldName" data-testid="seal-evidence-field">
        {{ line(field) }}
        <span v-if="field.together"> {{ t('sealVerification.evidence.together') }}</span>
      </li>
      <li v-if="evidence.issued" data-testid="seal-evidence-issued">
        {{ t('sealVerification.evidence.issued', {
          name: evidence.issued.issuerName ?? t('sealVerification.evidence.unknown'),
          time: formatDateTime(evidence.issued.signedAt),
        }) }}
      </li>
    </ul>
  </NeutralContainer>
</template>
