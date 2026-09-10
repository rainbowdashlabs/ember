/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import Alert from './Alert.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import {openProblemReport} from '@/util/problemReportState'
import type {Failure} from '@/util/failure'

/**
 * Something that went wrong, said in a way the reader can act on.
 *
 * <p>Three parts, and the third is the one that was missing everywhere: what happened, what to do about
 * it, and, where it looks like a fault in Ember rather than something the reader or their station can put
 * right, the offer to report it. A reader who is told to report something and handed the way to do it in
 * the same breath actually reports it.
 *
 * <p>Takes either a described {@link Failure} or a bare message. Where only a message is given, a report
 * is offered exactly when that message is the generic one: a screen that could say nothing better than
 * "that did not work" is itself evidence that something unexpected happened, whereas a screen quoting the
 * server's own sentence about a mistyped field has already told the reader what to do, and inviting a bug
 * report there only buries the real ones.
 */
const props = defineProps<{
  /** The failure, where the caller has one described. */
  failure?: Failure | null
  /** A message, for a caller that only has one. */
  message?: string
  /** Set where the message is something the reader mistyped, so no report is offered. */
  expected?: boolean
  /** Overrides the judgement above, for a caller that knows which of the two this is. */
  reportable?: boolean
}>()

const {t} = useI18n()

const shown = computed(() => props.failure?.message ?? props.message ?? '')

const guidance = computed(() => props.failure?.guidance)

const reportable = computed(() => {
  if (props.expected) return false
  if (props.reportable !== undefined) return props.reportable
  if (props.failure) return props.failure.reportable
  return shown.value !== '' && shown.value === t('common.error')
})

function report() {
  openProblemReport({
    summary: shown.value,
    technical: props.failure?.technical,
  })
}
</script>

<template>
  <Alert v-if="shown" variant="error">
    <div class="space-y-2">
      <p class="break-words">{{ shown }}</p>
      <p v-if="guidance" class="break-words opacity-90">{{ guidance }}</p>
      <div v-if="reportable">
        <SecondaryButton :icon="['fas', 'bug']" data-testid="failure-report" size="sm" @click="report">
          {{ t('failure.report') }}
        </SecondaryButton>
      </div>
    </div>
  </Alert>
</template>
