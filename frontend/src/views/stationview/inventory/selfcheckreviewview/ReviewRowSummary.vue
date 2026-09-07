/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import MutedText from '@/components/typography/MutedText.vue'
import IdentifierFinding from './IdentifierFinding.vue'
import type {SelfCheckReviewRow} from '@/api/selfChecks'

/**
 * What the member said about one thing, and what settling it would do.
 *
 * <p>Whether the gear comes in sizes is told rather than read off the answer, because a size nobody
 * gave and a kind of gear that has none look the same from here and only one of them is worth a
 * line on the screen.
 */
const props = defineProps<{
  row: SelfCheckReviewRow
  /** Whether this answer is one the member could have named a size on. */
  asksForASize: boolean
}>()

const {t} = useI18n()

const title = computed(() => props.row.item?.name ?? props.row.inventoryName)
</script>

<template>
  <div class="flex-1 min-w-0 space-y-1">
    <div class="font-medium text-sm">
      {{ title }}
      <SuccessBadge v-if="row.row.state === 'TAKEN'">{{ t('selfCheck.review.taken') }}</SuccessBadge>
      <ErrorBadge v-if="row.row.state === 'REFUSED'">{{ t('selfCheck.review.refused') }}</ErrorBadge>
      <InfoBadge v-if="row.borrowed">{{ t('selfCheck.borrowed') }}</InfoBadge>
      <InfoBadge v-if="row.recordedLost">{{ t('selfCheck.recordedLost') }}</InfoBadge>
    </div>
    <div class="text-sm" :data-testid="`review-answer-${row.row.id}`">
      {{ t(`selfCheck.answer.${row.row.answer}`) }}
    </div>
    <MutedText v-if="row.row.note" size="sm" tag="p">{{ row.row.note }}</MutedText>
    <MutedText v-if="row.statedSize" size="sm" tag="p" :data-testid="`review-stated-size-${row.row.id}`">
      {{ t('selfCheck.review.statedSize', {size: row.statedSize}) }}
    </MutedText>
    <MutedText v-else-if="asksForASize" size="sm" tag="p" :data-testid="`review-stated-size-${row.row.id}`">
      {{ t('selfCheck.review.noSizeGiven') }}
    </MutedText>
    <MutedText size="xs" tag="p">{{ t('selfCheck.review.answeredBy', {name: row.answeredByName}) }}</MutedText>
    <MutedText size="xs" tag="p" :data-testid="`review-settlement-${row.row.id}`">
      {{ t(`selfCheck.review.settlement.${row.settlement}`) }}
    </MutedText>
    <MutedText v-if="row.removal !== 'NOTHING'" size="xs" tag="p" :data-testid="`review-removal-${row.row.id}`">
      {{ t(`selfCheck.review.removal.${row.removal}`) }}
    </MutedText>
    <MutedText v-if="row.row.state === 'REFUSED'" size="xs" tag="p">
      {{ t('selfCheck.cameBack', {reason: row.row.reviewerReason}) }}
    </MutedText>
    <IdentifierFinding :identifier="row.identifier"/>
  </div>
</template>
