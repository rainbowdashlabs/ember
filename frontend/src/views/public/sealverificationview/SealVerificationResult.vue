/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import Alert from '@/components/feedback/Alert.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import type {SealVerification} from '@/api/generated/schema'
import DocumentTimestampList from './DocumentTimestampList.vue'
import HeldCopyNotice from './HeldCopyNotice.vue'
import SealCheckCard from './SealCheckCard.vue'

/**
 * What the check found in one file: a card per seal, or the plain statement that there is none, then
 * whether this installation keeps the file and any timestamps on the file as a whole.
 */
defineProps<{
  result: SealVerification
  fileName: string
}>()

const {t} = useI18n()
</script>

<template>
  <section class="space-y-4" data-testid="seal-verification-result">
    <SectionHeader>{{ t('sealVerification.result.title', {name: fileName}) }}</SectionHeader>
    <Alert v-if="result.signatures.length === 0" variant="info" data-testid="seal-none">
      <p class="font-medium">{{ t('sealVerification.result.noSeal') }}</p>
      <p>{{ t('sealVerification.result.noSealText') }}</p>
    </Alert>
    <SealCheckCard v-for="(check, index) in result.signatures" :key="index" :check="check"/>
    <HeldCopyNotice :document="result.document"/>
    <DocumentTimestampList v-if="result.documentTimestamps.length > 0" :timestamps="result.documentTimestamps"/>
  </section>
</template>
