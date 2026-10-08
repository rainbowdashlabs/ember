/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import {RequirementSignatureState, type RequirementSignature, type RequirementSignatureField} from '@/api/generated/schema'
import SignatureStateBadge from './SignatureStateBadge.vue'
import {signerLabel} from './requirementSignatures'

/**
 * The signature fields of a participant's copy, each with who signs it and where it stands. Where the
 * reader may sign a field now, for themselves or for a member in their care, and `offerSigning` is set,
 * the field opens the signing screen.
 */
const props = defineProps<{
  signature: RequirementSignature
  offerSigning: boolean
}>()

const {t} = useI18n()
const router = useRouter()

function signable(field: RequirementSignatureField): boolean {
  return props.offerSigning && field.yours && field.state === RequirementSignatureState.OPEN
}

function sign(field: RequirementSignatureField) {
  void router.push({name: 'station-signing', params: {fieldId: field.id}})
}
</script>

<template>
  <ul class="space-y-1 text-sm" data-testid="signature-fields">
    <li v-for="field in signature.fields" :key="field.id" class="flex flex-wrap items-center gap-2"
        data-testid="signature-field">
      <span class="flex-1">{{ signerLabel(field, t) }}</span>
      <SignatureStateBadge :state="field.state"/>
      <PrimaryButton v-if="signable(field)" :icon="['fas', 'file-signature']" data-testid="signature-field-sign"
                     @click="sign(field)">
        {{ t('events.documents.signOnline') }}
      </PrimaryButton>
    </li>
  </ul>
</template>
