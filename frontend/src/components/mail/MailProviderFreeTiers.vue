/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {PROVIDER_FREE_TIER, RELAY_PROVIDER_NAMES} from '@/util/mailProviders'

/**
 * What each relay lets a station send for nothing.
 *
 * Somebody choosing a provider is really asking one question first: is this free for what we send?
 * Answering it here saves opening four pricing pages, and naming the two providers that have no
 * free allowance is as useful as naming the two that do. Every figure carries the month it was
 * checked and a link, because these are somebody else's terms and they move.
 *
 * A station's own server is not listed: what it may send is its own business.
 */
const {t} = useI18n()

const entries = Object.entries(PROVIDER_FREE_TIER).map(([key, tier]) => ({
  key,
  name: RELAY_PROVIDER_NAMES[key] ?? t('mailFallbacks.ownServer'),
  ...tier,
}))
</script>

<template>
  <NeutralContainer class="space-y-3">
    <SectionHeader>{{ t('mailFreeTier.title') }}</SectionHeader>
    <MutedText tag="p" size="sm">{{ t('mailFreeTier.hint') }}</MutedText>

    <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-3">
      <component
          :is="entry.pricingUrl ? 'a' : 'div'"
          v-for="entry in entries" :key="entry.key"
          :href="entry.pricingUrl"
          :target="entry.pricingUrl ? '_blank' : undefined"
          :rel="entry.pricingUrl ? 'noopener' : undefined"
          class="block rounded-theme border border-(--border) p-3 space-y-1"
          :class="entry.pricingUrl ? 'hover:border-(--primary) transition-colors' : ''">
        <div class="font-medium">{{ entry.name }}</div>
        <div v-if="entry.perDay" class="text-2xl font-semibold text-(--primary)">
          {{ t('mailFreeTier.perDay', {count: entry.perDay}) }}
        </div>
        <div v-else class="text-sm font-medium text-(--text-muted)">
          {{ t(`mailFreeTier.${entry.noteKey}`) }}
        </div>
        <div class="text-xs text-(--text-muted)">
          {{ entry.checked ? t('mailFreeTier.checked', {date: entry.checked}) : t('mailFreeTier.noTerms') }}
        </div>
      </component>
    </div>
  </NeutralContainer>
</template>
