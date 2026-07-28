/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { QuizTestDetail } from '@/api/quiz'
import MutedText from '@/components/typography/MutedText.vue'

defineProps<{
  test: QuizTestDetail['test']
}>()

const { t } = useI18n()
</script>

<template>
  <div>
    <MutedText class="block">{{ t('quiz.tests.timeLimit') }}</MutedText>
    <span>{{ test.timeLimit ? t('quiz.tests.timeLimitValue', { minutes: test.timeLimit }) : t('quiz.tests.noTimeLimit') }}</span>
  </div>
  <div>
    <MutedText class="block">{{ t('quiz.tests.shuffle') }}</MutedText>
    <span>{{ test.shuffle ? t('common.yes') : t('common.no') }}</span>
  </div>
</template>
