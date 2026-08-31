/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Alert from '@/components/feedback/Alert.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {formatDate} from '@/util/format'
import {SelfCheckState, type SelfCheckSummary} from '@/api/selfChecks'

/** Whose gear this is about, by when it is wanted, and where the task stands. */
defineProps<{
  task: SelfCheckSummary
  outstandingCount: number
}>()

const {t} = useI18n()
</script>

<template>
  <div class="space-y-6">
    <NeutralContainer class="space-y-1">
      <div class="font-medium">{{ task.memberName }}</div>
      <MutedText v-if="task.dueOn" size="sm" data-testid="self-check-due">
        {{ t('selfCheck.dueOn', {date: formatDate(task.dueOn)}) }}
      </MutedText>
      <MutedText size="sm">{{ t('selfCheck.intro') }}</MutedText>
    </NeutralContainer>

    <Alert v-if="task.state === SelfCheckState.SUBMITTED" variant="info" data-testid="self-check-submitted">
      {{ t('selfCheck.waitingForReview', {count: outstandingCount}) }}
    </Alert>
    <Alert v-else-if="task.state !== SelfCheckState.OPEN" variant="info" data-testid="self-check-closed">
      {{ t('selfCheck.closed') }}
    </Alert>
  </div>
</template>
