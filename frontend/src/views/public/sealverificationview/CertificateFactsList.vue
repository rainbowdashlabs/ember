/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import DetailLabel from '@/components/typography/DetailLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {CertificateFacts} from '@/api/generated/schema'
import {fingerprintRows} from '@/util/fingerprint'

/**
 * One certificate as a reader compares it: whom it names, its serial number and its SHA-256
 * fingerprint, broken into rows the way the station's public page prints it.
 */
defineProps<{
  label: string
  certificate: CertificateFacts | null
}>()

const {t} = useI18n()
</script>

<template>
  <div class="space-y-1">
    <DetailLabel>{{ label }}</DetailLabel>
    <MutedText v-if="!certificate" tag="p" size="sm">{{ t('sealVerification.details.noCertificate') }}</MutedText>
    <dl v-else class="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-sm">
      <dt class="text-(--text-muted)">{{ t('sealVerification.details.subject') }}</dt>
      <dd class="break-all">{{ certificate.subject }}</dd>
      <dt class="text-(--text-muted)">{{ t('sealVerification.details.serial') }}</dt>
      <dd class="font-mono text-xs break-all">{{ certificate.serialNumber }}</dd>
      <dt class="text-(--text-muted)">{{ t('sealVerification.details.fingerprint') }}</dt>
      <dd class="font-mono text-xs">
        <span v-for="row in fingerprintRows(certificate.sha256Fingerprint)" :key="row" class="block break-all">{{ row }}</span>
      </dd>
    </dl>
  </div>
</template>
