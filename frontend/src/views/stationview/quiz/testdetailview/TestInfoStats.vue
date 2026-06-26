/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { QuizTestDetail } from '@/api/types'

defineProps<{
  test: QuizTestDetail['test']
}>()

const { t } = useI18n()
</script>

<template>
  <div>
    <span class="text-xs text-(--text-muted) block">{{ t('quiz.tests.timeLimit') }}</span>
    <span>{{ test.timeLimit ? t('quiz.tests.timeLimitValue', { minutes: test.timeLimit }) : t('quiz.tests.noTimeLimit') }}</span>
  </div>
  <div>
    <span class="text-xs text-(--text-muted) block">{{ t('quiz.tests.shuffle') }}</span>
    <span>{{ test.shuffle ? t('common.yes') : t('common.no') }}</span>
  </div>
</template>
