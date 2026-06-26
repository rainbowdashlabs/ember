/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SectionLabel from '@/components/typography/SectionLabel.vue'

const {t} = useI18n()
</script>

<template>
  <NeutralContainer>
    <div class="space-y-3">
      <SectionLabel>{{ t('quiz.evaluate.title') }}</SectionLabel>
      <div>
        <FieldLabel hint class="mb-1">{{ t('quiz.evaluate.studentAnswer') }}</FieldLabel>
        <p class="text-sm px-3 py-2 rounded border border-bg-light-accent dark:border-bg-dark-accent">Wasser</p>
      </div>
      <div>
        <label class="text-xs text-success block mb-1">{{ t('quiz.evaluate.correctAnswer') }}</label>
        <p class="text-sm px-3 py-2 rounded border border-success/30 bg-success/10">Wasser, Schaum</p>
      </div>
      <div class="flex items-center gap-2">
        <label class="text-sm font-medium">{{ t('quiz.evaluate.points') }}</label>
        <NumberInput :model-value="1" disabled class="w-24" />
        <span class="text-sm text-(--text-muted)">/ 2</span>
      </div>
    </div>
  </NeutralContainer>
</template>
