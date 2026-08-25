/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'

const {t} = useI18n()

const steps = [
  {number: 1, title: 'step1Title', text: 'step1Text', icon: 'file-arrow-up'},
  {number: 2, title: 'step2Title', text: 'step2Text', icon: 'table-columns'},
  {number: 3, title: 'step3Title', text: 'step3Text', icon: 'eye'},
]
</script>

<template>
  <HelpSection :title="t('helpCenter.quizCatalogImport.stepsTitle')">
    <NeutralContainer v-for="step in steps" :key="step.number" class="space-y-1">
      <div class="flex items-center gap-2">
        <font-awesome-icon :icon="['fas', step.icon]" />
        <SectionHeader>{{ step.number }}. {{ t(`helpCenter.quizCatalogImport.${step.title}`) }}</SectionHeader>
      </div>
      <MutedText class="block text-sm">{{ t(`helpCenter.quizCatalogImport.${step.text}`) }}</MutedText>
    </NeutralContainer>
  </HelpSection>
</template>
