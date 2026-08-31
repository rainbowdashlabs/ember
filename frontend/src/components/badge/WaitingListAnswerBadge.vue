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
import type {WaitingListAnswerName} from '@/api/waitingList'

/**
 * What somebody answered to their invitation, shown beside the entry rather than filed away.
 *
 * A refusal keeps the entry in the open section: an answer that disappears on arrival is the same
 * failure as no answer at all.
 */
const props = defineProps<{answer: WaitingListAnswerName}>()

const {t} = useI18n()

function badge(answer: WaitingListAnswerName) {
  if (answer === 'COMING') return SuccessBadge
  if (answer === 'NOT_INTERESTED') return ErrorBadge
  return InfoBadge
}
</script>

<template>
  <component :is="badge(props.answer)" data-testid="waitlist-answer-badge">
    {{ t('waitingList.answer_' + props.answer) }}
  </component>
</template>
