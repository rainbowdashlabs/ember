/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import BulletList from '@/components/typography/BulletList.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'

/**
 * What a station seal proves and what it does not, and how a reader checks the seal against
 * something published apart from the file. A seal is a simple electronic signature of the station,
 * and nothing here may suggest more than that.
 */
const {t} = useI18n()
</script>

<template>
  <section class="space-y-3" data-testid="seal-verification-explainer">
    <SectionHeader>{{ t('sealVerification.explain.title') }}</SectionHeader>
    <MutedText tag="p" size="sm">{{ t('sealVerification.explain.what') }}</MutedText>
    <BulletList>
      <li>{{ t('sealVerification.explain.proves1') }}</li>
      <li>{{ t('sealVerification.explain.proves2') }}</li>
      <li>{{ t('sealVerification.explain.provesNot1') }}</li>
      <li>{{ t('sealVerification.explain.provesNot2') }}</li>
    </BulletList>
    <MutedText tag="p" size="sm">{{ t('sealVerification.explain.compare') }}</MutedText>
    <MutedText tag="p" size="sm">{{ t('sealVerification.explain.elsewhere') }}</MutedText>
  </section>
</template>
