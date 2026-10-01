/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import Alert from '@/components/feedback/Alert.vue'
import {CancellationCauses} from '@/api/events'
import type {CancellationNotice} from '@/api/generated/schema'
import {formatDateTime} from '@/util/format'

/**
 * Says that the date on screen is off, and why: the reason a manager gave, or that too few had
 * registered in time. A whole series called off says so instead of naming the date.
 */
const props = defineProps<{
  cancellation: CancellationNotice
  /** Whether the whole series is off rather than this one date. */
  seriesCancelled: boolean
}>()

const {t} = useI18n()

const heading = computed(() => props.seriesCancelled ? t('events.seriesCancelled') : t('events.dateCancelled'))

const why = computed(() => {
  if (props.cancellation.cause === CancellationCauses.THRESHOLD) return t('events.cancelledTooFewRegistrations')
  return props.cancellation.reason ?? ''
})
</script>

<template>
  <Alert variant="error" data-testid="event-cancelled">
    <span class="font-bold">{{ heading }}</span>
    <span v-if="why"> - {{ why }}</span>
    <span v-if="cancellation.cancelledAt" class="text-xs opacity-75 ml-2">{{ formatDateTime(cancellation.cancelledAt) }}</span>
  </Alert>
</template>
