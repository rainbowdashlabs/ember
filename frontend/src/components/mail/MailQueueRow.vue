/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import MutedText from '@/components/typography/MutedText.vue'
import type {MailRecord} from '@/api/mailProviders'

/**
 * One mail and what became of it.
 *
 * A provider accepting a message and a message arriving are two different things, so the row shows
 * both: the queue status says whether Ember got rid of it, the delivery status says what the
 * provider reported afterwards. The reason a provider gave is worth more than either.
 */
const props = defineProps<{
  entry: MailRecord
}>()

const {t} = useI18n()

/** Delivery states that mean the mail did not arrive, which is what a reader is scanning for. */
const BAD = ['SOFT_BOUNCE', 'HARD_BOUNCE', 'BLOCKED', 'SPAM']

const tone = computed(() => {
  if (BAD.includes(props.entry.deliveryStatus)) return 'border-(--error)'
  if (props.entry.status === 'FAILED') return 'border-(--error)'
  if (stuck.value) return 'border-(--error)'
  if (props.entry.deliveryStatus === 'DELIVERED') return 'border-(--success)'
  return 'border-(--border)'
})

/**
 * Waiting and unable to go anywhere. Worth saying outright: a queue that is merely busy looks the
 * same as one that will never move, and only one of them needs somebody to act.
 */
const stuck = computed(() =>
    ['PENDING', 'SENDING'].includes(props.entry.status) && !props.entry.reachable)

const when = computed(() => new Date(props.entry.sentAt ?? props.entry.createdAt).toLocaleString('de-DE'))
</script>

<template>
  <div class="rounded-lg border-l-4 border border-(--border) p-3 space-y-1" :class="tone">
    <div class="flex items-baseline justify-between gap-2 flex-wrap">
      <span class="font-medium break-all">{{ entry.recipient }}</span>
      <MutedText tag="span" size="sm">{{ when }}</MutedText>
    </div>
    <div class="text-sm break-words">{{ entry.subject }}</div>
    <div class="text-xs text-(--text-muted) flex gap-2 flex-wrap">
      <span>{{ entry.status }}</span>
      <span v-if="entry.deliveryStatus && entry.deliveryStatus !== 'UNKNOWN'">· {{ entry.deliveryStatus }}</span>
      <span>· {{ t('mailDashboard.viaProvider', {position: entry.providerPosition + 1}) }}</span>
      <span v-if="entry.attempts > 0">· {{ t('mailDashboard.attemptsMade', {count: entry.attempts}) }}</span>
    </div>
    <div v-if="stuck" class="text-xs text-(--error)">{{ t('mailDashboard.unreachable') }}</div>
    <div v-if="entry.deliveryDetail" class="text-xs text-(--error) break-words">{{ entry.deliveryDetail }}</div>
  </div>
</template>
