/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'

const { t } = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.quizTestCreate.title')" :subtitle="t('helpCenter.quizTestCreate.subtitle')">
    <HelpSection :title="t('helpCenter.quizTestCreate.whatIs')">
      <p>{{ t('helpCenter.quizTestCreate.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.quizTestCreate.howTo')">
      <p>{{ t('helpCenter.quizTestCreate.step1') }}</p>
      <p>{{ t('helpCenter.quizTestCreate.step2') }}</p>
      <p>{{ t('helpCenter.quizTestCreate.step3') }}</p>
    </HelpSection>

    <!-- Dummy: Create form -->
    <NeutralContainer class="space-y-3">
      <TextInput model-value="Jugendflamme Stufe 1 — Wissenstest" disabled />
      <TextInput model-value="Theoretischer Teil der Prüfung" disabled />
      <PrimaryButton disabled class="w-full justify-center">
        {{ t('helpCenter.quizTestCreate.createButton') }}
      </PrimaryButton>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.quizTestCreate.nextTitle')">
      <p>{{ t('helpCenter.quizTestCreate.nextText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.quizTestCreate.tip') }}</HelpTip>
  </HelpArticle>
</template>
