/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import {TaskOutcome, type TaskOutcomeName} from '@/api/adminTasks'

/** How the last run of a background task went, in the colour the rest of the admin area uses for it. */
defineProps<{
  outcome: TaskOutcomeName
}>()

const {t} = useI18n()
</script>

<template>
  <ErrorBadge v-if="outcome === TaskOutcome.FAILED">{{ t('adminTasks.outcomeFailed') }}</ErrorBadge>
  <InfoBadge v-else-if="outcome === TaskOutcome.RUNNING">{{ t('adminTasks.outcomeRunning') }}</InfoBadge>
  <SuccessBadge v-else-if="outcome === TaskOutcome.SUCCEEDED">{{ t('adminTasks.outcomeSucceeded') }}</SuccessBadge>
  <SecondaryBadge v-else>{{ t('adminTasks.outcomeNotRunYet') }}</SecondaryBadge>
</template>
