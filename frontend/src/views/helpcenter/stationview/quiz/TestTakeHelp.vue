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
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'

const { t } = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.quizTestTake.title')" :subtitle="t('helpCenter.quizTestTake.subtitle')">
    <HelpSection :title="t('helpCenter.quizTestTake.whatIs')">
      <p>{{ t('helpCenter.quizTestTake.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.quizTestTake.howTo')">
      <p>{{ t('helpCenter.quizTestTake.step1') }}</p>
      <p>{{ t('helpCenter.quizTestTake.step2') }}</p>
      <p>{{ t('helpCenter.quizTestTake.step3') }}</p>
    </HelpSection>

    <!-- Dummy: Question view -->
    <NeutralContainer class="space-y-3">
      <div class="text-xs text-[var(--text-muted)] uppercase font-semibold">Frage 3 von 15</div>
      <p class="font-medium">Welche Notrufnummer wählst du bei einem Feuer?</p>
      <div class="space-y-2 text-sm">
        <FieldLabel inline class="cursor-not-allowed">
          <ToggleInput :model-value="false" disabled /> 110
        </FieldLabel>
        <FieldLabel inline class="cursor-not-allowed">
          <ToggleInput :model-value="true" disabled /> 112
        </FieldLabel>
        <FieldLabel inline class="cursor-not-allowed">
          <ToggleInput :model-value="false" disabled /> 118
        </FieldLabel>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.quizTestTake.submitTitle')">
      <p>{{ t('helpCenter.quizTestTake.submitText') }}</p>
    </HelpSection>

    <!-- Dummy: Submit button -->
    <PrimaryButton disabled class="w-full justify-center">
      {{ t('helpCenter.quizTestTake.submitButton') }}
    </PrimaryButton>

    <HelpTip>{{ t('helpCenter.quizTestTake.tip') }}</HelpTip>
  </HelpArticle>
</template>
