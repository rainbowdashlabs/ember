/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SuccessButton from '@/components/button/SuccessButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import type {SelfCheckReviewRow} from '@/api/selfChecks'

/**
 * What a reviewer may do with one answer.
 *
 * <p>Taking and correcting are never both offered: an answer whose record has to be put right first
 * is corrected and taken in one act, so offering a bare take beside it would be an act that refuses.
 */
const props = defineProps<{
  row: SelfCheckReviewRow
  busy: boolean
}>()

const emit = defineEmits<{
  take: [rowId: number]
  correct: [row: SelfCheckReviewRow]
  refuse: [row: SelfCheckReviewRow]
}>()

const {t} = useI18n()

const needsCorrection = computed(
  () => props.row.settlement === 'NEEDS_RECORD_PUT_RIGHT' || props.row.settlement === 'NEEDS_A_PIECE_NAMED',
)
</script>

<template>
  <div class="flex flex-wrap gap-1 shrink-0">
    <SuccessButton
        v-if="!needsCorrection"
        class="text-xs px-3 py-1.5"
        :disabled="busy"
        :data-testid="`review-take-${row.row.id}`"
        @click="emit('take', row.row.id)"
    >
      {{ t('selfCheck.review.take') }}
    </SuccessButton>
    <SecondaryButton
        v-else
        class="text-xs px-3 py-1.5"
        :disabled="busy"
        :data-testid="`review-correct-${row.row.id}`"
        @click="emit('correct', row)"
    >
      {{ t('selfCheck.review.correct') }}
    </SecondaryButton>
    <ErrorButton
        class="text-xs px-3 py-1.5"
        :disabled="busy"
        :data-testid="`review-refuse-${row.row.id}`"
        @click="emit('refuse', row)"
    >
      {{ t('selfCheck.review.refuse') }}
    </ErrorButton>
  </div>
</template>
