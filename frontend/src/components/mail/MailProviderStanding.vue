/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import MutedText from '@/components/typography/MutedText.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import {RELAY_PROVIDER_NAMES} from '@/util/mailProviders'
import type {ProviderStanding} from '@/api/mailProviders'

/**
 * How one provider stands today: what it has sent against what it may, and how much post is
 * waiting at it. A provider whose allowance is spent says so, because that is the moment the one
 * below it starts carrying the mail.
 */
const props = defineProps<{
  standing: ProviderStanding
}>()

const {t} = useI18n()

const name = computed(() => RELAY_PROVIDER_NAMES[props.standing.provider] ?? t('mailChain.ownServer'))

/** How full the day's allowance is, or null when the provider has none. */
const share = computed(() => {
  if (props.standing.dailySendLimit <= 0) return null
  return Math.min(100, Math.round((props.standing.sentToday / props.standing.dailySendLimit) * 100))
})
</script>

<template>
  <div class="rounded-lg border border-(--border) p-3 space-y-2">
    <div class="flex items-center gap-2 flex-wrap">
      <span class="font-medium">{{ standing.position + 1 }}. {{ name }}</span>
      <MutedText tag="span" size="sm">{{ standing.senderAddress }}</MutedText>
      <PrimaryBadge v-if="standing.waiting > 0">
        {{ t('mailDashboard.waitingHere', {count: standing.waiting}) }}
      </PrimaryBadge>
      <ErrorBadge v-if="standing.exhausted">{{ t('mailDashboard.exhausted') }}</ErrorBadge>
    </div>

    <div class="text-sm text-(--text-muted)">
      {{ standing.dailySendLimit > 0
        ? t('mailDashboard.sentOfLimit', {sent: standing.sentToday, limit: standing.dailySendLimit})
        : t('mailDashboard.sentNoLimit', {sent: standing.sentToday}) }}
      <span class="mx-1">·</span>
      {{ t('mailDashboard.attempts', {count: standing.attempts}) }}
    </div>

    <div v-if="share !== null" class="h-1.5 w-full rounded-full bg-(--bg-accent) overflow-hidden">
      <div class="h-full rounded-full bg-(--primary)" :style="{width: `${share}%`}"/>
    </div>
  </div>
</template>
