/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import MutedText from '@/components/typography/MutedText.vue'
import SectionLabel from '@/components/typography/SectionLabel.vue'
import type {SigningAuthorityInfo} from '@/api/generated/schema'
import {fingerprintRows} from '@/util/fingerprint'

/**
 * The fingerprint of each signing authority a station's documents are sealed under, with its
 * certificate and revocation list to download, so a reader can check a sealed document against
 * something published apart from the document itself, and the way to the page that checks one.
 */
defineProps<{
  authorities: SigningAuthorityInfo[]
}>()

const {t} = useI18n()

function authorityAddress(serialNumber: string, suffix: 'crt' | 'crl'): string {
  return `/api/v1/public/signing/ca/${serialNumber}.${suffix}`
}
</script>

<template>
  <section class="max-w-3xl mx-auto px-4 py-6 space-y-2" data-testid="public-station-seal">
    <SectionLabel>{{ t('publicStation.seal.title') }}</SectionLabel>
    <MutedText tag="p" size="sm">{{ t('publicStation.seal.hint') }}</MutedText>
    <div v-for="authority in authorities" :key="authority.serialNumber" class="space-y-1">
      <MutedText tag="p">{{ t('publicStation.seal.fingerprint') }}</MutedText>
      <code class="block font-mono text-xs break-all">
        <span v-for="row in fingerprintRows(authority.sha256Fingerprint)" :key="row" class="block">{{ row }}</span>
      </code>
      <div class="flex flex-wrap gap-x-4 text-sm">
        <a :href="authorityAddress(authority.serialNumber, 'crt')" class="text-(--link) hover:underline">
          {{ t('publicStation.seal.certificate') }}
        </a>
        <a :href="authorityAddress(authority.serialNumber, 'crl')" class="text-(--link) hover:underline">
          {{ t('publicStation.seal.revocationList') }}
        </a>
      </div>
    </div>
    <NuxtLink to="/verify" class="inline-block text-sm text-(--link) hover:underline">
      {{ t('publicStation.seal.verify') }}
    </NuxtLink>
  </section>
</template>
