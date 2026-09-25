/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import MutedIcon from '@/components/display/MutedIcon.vue'
import {QuizTestStatus, type QuizTest} from '@/api/quiz'

/** What a test sheet says about itself beside its name: who may sit it, where it stands, whether it was. */
defineProps<{
  test: QuizTest
  submitted: boolean
}>()

const {t} = useI18n()
</script>

<template>
  <MutedIcon v-if="test.restricted" :icon="['fas', 'lock']" class="ml-1"/>
  <SuccessBadge v-if="test.status === QuizTestStatus.ACTIVE">{{ t('quiz.tests.statusActive') }}</SuccessBadge>
  <ErrorBadge v-else-if="test.status === QuizTestStatus.CLOSED">{{ t('quiz.tests.statusClosed') }}</ErrorBadge>
  <SecondaryBadge v-else>{{ t('quiz.tests.statusDraft') }}</SecondaryBadge>
  <InfoBadge v-if="submitted">{{ t('quiz.tests.taken') }}</InfoBadge>
</template>
