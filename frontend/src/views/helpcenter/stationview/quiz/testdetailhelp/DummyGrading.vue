/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'

const {t} = useI18n()
</script>

<template>
  <NeutralContainer>
    <div class="space-y-3">
      <div class="flex items-center justify-between">
        <span class="font-medium text-sm">Max Mustermann</span>
        <div class="flex items-center gap-2">
          <span class="text-sm">8 / 10 {{ t('quiz.points') }}</span>
          <SecondaryButton disabled>{{ t('quiz.evaluate.title') }}</SecondaryButton>
        </div>
      </div>
      <div class="flex items-center justify-between">
        <span class="font-medium text-sm">Lisa Beispiel</span>
        <div class="flex items-center gap-2">
          <InfoBadge>{{ t('quiz.statusSubmitted') }}</InfoBadge>
          <PrimaryButton disabled>{{ t('quiz.evaluate.title') }}</PrimaryButton>
        </div>
      </div>
    </div>
  </NeutralContainer>
</template>
