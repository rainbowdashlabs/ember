/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import type {QuizAttemptDetail} from '@/api/types'

const props = defineProps<{
  attempt: QuizAttemptDetail
  totalPoints: number
  maxPoints: number
}>()

const {t} = useI18n()
</script>

<template>
  <div class="flex items-center justify-between flex-wrap gap-4">
    <div>
      <SubHeader>{{ t('quiz.evaluate.title') }}</SubHeader>
      <MemberName :identity="props.attempt.memberIdentity" size="md" class="mt-1" />
    </div>
    <div class="flex items-center gap-2">
      <span class="text-sm font-medium">
        {{ props.totalPoints }} / {{ props.maxPoints }} {{ t('quiz.points') }}
      </span>
    </div>
  </div>
</template>
