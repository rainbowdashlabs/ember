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
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import MailPoolStanding from './MailPoolStanding.vue'
import {RELAY_PROVIDER_NAMES} from '@/util/mailProviders'
import type {ProviderStanding} from '@/api/generated/schema'

/**
 * How one provider stands today: what it has sent against what it may, and how much post is
 * waiting at it. A provider whose allowance is spent says so, because that is the moment the one
 * below it starts carrying the mail.
 *
 * <p>One of the instance's providers at the end of a station's list counts the station's own mail
 * only, against the share all stations may use of it; the provider's own daily limit belongs to the
 * instance and is not the station's to read against.
 */
const props = defineProps<{
  standing: ProviderStanding
}>()

const {t} = useI18n()

const name = computed(() => RELAY_PROVIDER_NAMES[props.standing.provider] ?? t('mailChain.ownServer'))

/** Whether the line reads against the provider's own daily limit. */
const ownLimit = computed(() => !props.standing.viaInstance && props.standing.dailySendLimit > 0)

/** What the provider sent today, in words. */
const sentLine = computed(() => {
  const {sentToday: sent, dailySendLimit: limit} = props.standing
  if (ownLimit.value) return t('mailDashboard.sentOfLimit', {sent, limit})
  if (props.standing.viaInstance) return t('instanceMail.pool.sentByStation', {sent})
  return t('mailDashboard.sentNoLimit', {sent})
})

/** How full the day's allowance is, or null when the provider has none to read against. */
const share = computed(() => {
  if (!ownLimit.value) return null
  return Math.min(100, Math.round((props.standing.sentToday / props.standing.dailySendLimit) * 100))
})
</script>

<template>
  <div class="rounded-lg border border-(--border) p-3 space-y-2">
    <div class="flex items-center gap-2 flex-wrap">
      <span class="font-medium">{{ standing.position + 1 }}. {{ name }}</span>
      <MutedText tag="span" size="sm">{{ standing.senderAddress }}</MutedText>
      <SecondaryBadge v-if="standing.viaInstance">{{ t('instanceMail.pool.instanceBadge') }}</SecondaryBadge>
      <PrimaryBadge v-if="standing.waiting > 0">
        {{ t('mailDashboard.waitingHere', {count: standing.waiting}) }}
      </PrimaryBadge>
      <ErrorBadge v-if="standing.exhausted">{{ t('mailDashboard.exhausted') }}</ErrorBadge>
    </div>

    <div class="text-sm text-(--text-muted)">
      {{ sentLine }}
      <span class="mx-1">·</span>
      {{ t('mailDashboard.attempts', {count: standing.attempts}) }}
    </div>

    <MailPoolStanding v-if="standing.pool" :pool="standing.pool"/>

    <div v-if="share !== null" class="h-1.5 w-full rounded-full bg-(--bg-accent) overflow-hidden">
      <div class="h-full rounded-full bg-(--color-primary)" :style="{width: `${share}%`}"/>
    </div>
  </div>
</template>
