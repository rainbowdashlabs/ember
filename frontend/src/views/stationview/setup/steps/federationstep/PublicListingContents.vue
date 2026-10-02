/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'

/**
 * Exactly what a public listing hands out: the fields of the card this instance publishes to the
 * discovery page and to other instances, where it travels, and where the choice is changed later.
 */
const {t} = useI18n()

const items = computed(() => [
  t('setup.steps.federation.listingName'),
  t('setup.steps.federation.listingDescription'),
  t('setup.steps.federation.listingLogo'),
  t('setup.steps.federation.listingPlace'),
  t('setup.steps.federation.listingAddress'),
  t('setup.steps.federation.listingMap'),
  t('setup.steps.federation.listingMembers'),
  t('setup.steps.federation.listingCluster'),
  t('setup.steps.federation.listingPage'),
])
</script>

<template>
  <NeutralContainer class="space-y-2" data-testid="public-listing-contents">
    <SubHeader>{{ t('setup.steps.federation.listingTitle') }}</SubHeader>
    <p class="text-sm">{{ t('setup.steps.federation.listingIntro') }}</p>
    <ul class="list-disc pl-5 text-sm space-y-0.5">
      <li v-for="item in items" :key="item">{{ item }}</li>
    </ul>
    <MutedText tag="p" size="sm">{{ t('setup.steps.federation.listingTravels') }}</MutedText>
    <MutedText tag="p" size="sm">{{ t('setup.steps.federation.changeLater') }}</MutedText>
  </NeutralContainer>
</template>
