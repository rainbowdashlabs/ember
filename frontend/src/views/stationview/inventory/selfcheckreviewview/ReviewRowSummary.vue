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

/** What the member said about one thing, and what settling it would do. */
const props = defineProps<{row: SelfCheckReviewRow}>()

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
    <MutedText v-if="row.row.note" size="sm">{{ row.row.note }}</MutedText>
    <MutedText size="xs">{{ t('selfCheck.review.answeredBy', {name: row.answeredByName}) }}</MutedText>
    <MutedText size="xs" :data-testid="`review-settlement-${row.row.id}`">
      {{ t(`selfCheck.review.settlement.${row.settlement}`) }}
    </MutedText>
    <MutedText v-if="row.removal !== 'NOTHING'" size="xs" :data-testid="`review-removal-${row.row.id}`">
      {{ t(`selfCheck.review.removal.${row.removal}`) }}
    </MutedText>
    <MutedText v-if="row.row.state === 'REFUSED'" size="xs">
      {{ t('selfCheck.cameBack', {reason: row.row.reviewerReason}) }}
    </MutedText>
    <IdentifierFinding :identifier="row.identifier"/>
  </div>
</template>
