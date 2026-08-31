/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import ReviewRowSummary from './ReviewRowSummary.vue'
import ReviewRowActions from './ReviewRowActions.vue'
import type {SelfCheckReviewRow} from '@/api/selfChecks'

/**
 * One answer as the reviewer reads it, with what settling it would do beside it.
 *
 * <p>Correcting is the ordinary case rather than the exception. A row saying a piece is gone against
 * a record the station got wrong is a true statement pointing at the wrong row, and sending it back
 * would ask the member to solve a problem only the station can see.
 */
const props = defineProps<{
  row: SelfCheckReviewRow
  busy: boolean
  mayApprove: boolean
}>()

const emit = defineEmits<{
  take: [rowId: number]
  correct: [row: SelfCheckReviewRow]
  refuse: [row: SelfCheckReviewRow]
}>()

const settleable = computed(() => props.row.row.state === 'OUTSTANDING' && props.mayApprove)
</script>

<template>
  <div
      class="rounded border border-bg-light-accent/50 dark:border-bg-dark-accent/50 p-3"
      :data-testid="`review-row-${row.row.id}`"
  >
    <div class="flex flex-col sm:flex-row sm:items-start gap-2">
      <ReviewRowSummary :row="row"/>
      <ReviewRowActions
          v-if="settleable"
          :row="row"
          :busy="busy"
          @take="id => emit('take', id)"
          @correct="r => emit('correct', r)"
          @refuse="r => emit('refuse', r)"
      />
    </div>
  </div>
</template>
