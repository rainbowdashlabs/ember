/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {SelfCheckRaisedView} from '@/api/selfChecks'

/**
 * What the member set going beside their answers, in the two groups a reviewer has to tell apart.
 *
 * <p>The first went out on its own and asks nothing of the reviewer. The second has not gone
 * anywhere: it hangs on an answer saying the record has the wrong size, so putting that record right
 * is what sends it, and sending the answer back is what drops it. A reviewer who cannot see which
 * group a report is in cannot know that their next click posts a loss.
 */
const props = defineProps<{reports: SelfCheckRaisedView[]}>()

const {t} = useI18n()

const wentOut = computed(() => props.reports.filter(entry => entry.raised.state === 'RAISED'))
const stillWaiting = computed(() => props.reports.filter(entry => entry.raised.state === 'WAITING'))

/** One report in the words the reviewer reads, which differ by whether it has happened yet. */
function says(entry: SelfCheckRaisedView, group: 'raised' | 'waiting'): string {
  return t(`selfCheck.review.${group}.${entry.raised.kind}`, {item: entry.itemName, name: entry.raisedByName})
}
</script>

<template>
  <NeutralContainer v-if="wentOut.length > 0" class="space-y-2">
    <SubHeader>{{ t('selfCheck.review.raisedTitle') }}</SubHeader>
    <MutedText size="sm" tag="p">{{ t('selfCheck.review.raisedHint') }}</MutedText>
    <div v-for="entry in wentOut" :key="entry.raised.id" class="text-sm" data-testid="review-raised">
      {{ says(entry, 'raised') }}
    </div>
  </NeutralContainer>

  <NeutralContainer v-if="stillWaiting.length > 0" class="space-y-2">
    <SubHeader>{{ t('selfCheck.review.waitingTitle') }}</SubHeader>
    <MutedText size="sm" tag="p">{{ t('selfCheck.review.waitingHint') }}</MutedText>
    <div v-for="entry in stillWaiting" :key="entry.raised.id" class="text-sm" data-testid="review-waiting">
      {{ says(entry, 'waiting') }}
    </div>
  </NeutralContainer>
</template>
