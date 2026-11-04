/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import MutedText from '@/components/typography/MutedText.vue'
import WaitingListAnswerBadge from '@/components/badge/WaitingListAnswerBadge.vue'
import type {WaitingListEntryWithScore} from '@/api/generated/schema'

/**
 * What the family answered to the dates they hold, shown where the station picks other ones, so the
 * new dates can reply to what was said.
 */
const props = defineProps<{entry: WaitingListEntryWithScore}>()

const {t} = useI18n()
</script>

<template>
  <div class="space-y-1 text-sm" data-testid="waitlist-other-dates-answer">
    <template v-if="props.entry.entry.answer">
      <WaitingListAnswerBadge :answer="props.entry.entry.answer.answer" />
      <p v-if="props.entry.entry.answer.note" class="whitespace-pre-line">
        {{ t('waitingList.answerNote') }}: {{ props.entry.entry.answer.note }}
      </p>
    </template>
    <MutedText v-else tag="p">{{ t('waitingList.otherDatesUnanswered') }}</MutedText>
  </div>
</template>
