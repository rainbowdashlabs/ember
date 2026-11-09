/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import RequirementCard from './RequirementCard.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import type {SignatureItem} from '@/api/generated/schema'

/**
 * The documents waiting for the reader's signature, each opening the signing screen for its field.
 * Whose document it is shows only where it is not the reader's own, and the procedure it is asked for where
 * one asks. Where more than one waits, one action above them signs them all in one go.
 */
defineProps<{signatures: SignatureItem[]}>()

const {t} = useI18n()
const router = useRouter()

function textOf(signature: SignatureItem): string {
  const text = signature.memberName
      ? t('requirements.signatureTextFor', {name: signature.memberName})
      : t('requirements.signatureText')
  return signature.purpose ? `${text} ${t('requirements.signatureForProcedure', {procedure: signature.purpose})}` : text
}
</script>

<template>
  <div v-if="signatures.length > 1" class="flex justify-end">
    <PrimaryButton :icon="['fas', 'file-signature']" data-testid="requirement-sign-all"
                   @click="router.push({name: 'station-signing-all'})">
      {{ t('requirements.signAll') }}
    </PrimaryButton>
  </div>
  <RequirementCard
      v-for="signature in signatures"
      :key="signature.fieldId"
      icon="file-signature"
      :title="signature.documentTitle ?? t('requirements.signatureTitle')"
      :text="textOf(signature)"
      :action="t('requirements.sign')"
      data-testid="requirement-signature"
      @open="router.push({name: 'station-signing', params: {fieldId: signature.fieldId}})"
  />
</template>
