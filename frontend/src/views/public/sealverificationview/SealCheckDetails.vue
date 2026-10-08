/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import MutedText from '@/components/typography/MutedText.vue'
import CertificateFactsList from './CertificateFactsList.vue'
import IndicationText from './IndicationText.vue'
import TimestampDetails from './TimestampDetails.vue'
import type {SealCheck} from '@/api/generated/schema'

/**
 * The technical side of one seal, closed until asked for: both verdicts under their ETSI names, the
 * baseline level, whether the signed span reaches the end of the file, the certificates with their
 * fingerprints, and each timestamp inside the seal.
 */
defineProps<{
  check: SealCheck
}>()

const {t} = useI18n()

function yesNo(value: boolean): string {
  return value ? t('common.yes') : t('common.no')
}
</script>

<template>
  <details class="text-sm" data-testid="seal-check-details">
    <summary class="cursor-pointer text-(--text-muted) hover:text-(--text) transition-colors">
      {{ t('sealVerification.details.summary') }}
    </summary>
    <div class="mt-3 space-y-4">
      <dl class="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1">
        <dt class="text-(--text-muted)">{{ t('sealVerification.details.indication') }}</dt>
        <dd><IndicationText :indication="check.indication" :sub-indication="check.subIndication"/></dd>
        <dt class="text-(--text-muted)">{{ t('sealVerification.details.validatorIndication') }}</dt>
        <dd><IndicationText :indication="check.validatorIndication" :sub-indication="check.validatorSubIndication"/></dd>
        <dt class="text-(--text-muted)">{{ t('sealVerification.details.level') }}</dt>
        <dd>{{ t(`sealVerification.level.${check.level}`) }}</dd>
        <dt class="text-(--text-muted)">{{ t('sealVerification.details.intact') }}</dt>
        <dd>{{ yesNo(check.intact) }}</dd>
        <dt class="text-(--text-muted)">{{ t('sealVerification.details.coversWholeFile') }}</dt>
        <dd>{{ yesNo(check.coversWholeFile) }}</dd>
      </dl>
      <MutedText tag="p">{{ t('sealVerification.details.coversWholeFileHint') }}</MutedText>
      <CertificateFactsList :label="t('sealVerification.details.signer')" :certificate="check.signer"/>
      <CertificateFactsList :label="t('sealVerification.details.issuer')" :certificate="check.issuer"/>
      <TimestampDetails v-for="(stamp, index) in check.timestamps" :key="index" :stamp="stamp"/>
    </div>
  </details>
</template>
