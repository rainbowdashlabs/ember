/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'

const {t} = useI18n()
</script>

<template>
  <NeutralContainer>
    <div class="space-y-2">
      <div class="flex items-center justify-between">
        <span class="font-medium text-sm">{{ t('helpCenter.sample.quiz.sectionBasics') }}</span>
        <span class="text-xs text-(--text-muted)">5 {{ t('quiz.questions.title') }}</span>
      </div>
      <p class="text-xs text-(--text-muted)">{{ t('helpCenter.sample.quiz.sectionSource') }}</p>
    </div>
  </NeutralContainer>
</template>
