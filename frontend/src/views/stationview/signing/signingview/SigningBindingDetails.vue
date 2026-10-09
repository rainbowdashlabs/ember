/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import MutedText from '@/components/typography/MutedText.vue'
import type {BatchStartResponse} from '@/api/generated/schema'

/** How many hexadecimal digits a group of a document's hash holds, as it is read off by eye. */
const DIGITS_PER_GROUP = 8

/**
 * What the confirmation binds to, behind "Mehr erfahren" for a signer who wants to compare it later: per
 * document the SHA-256 of the file as it was frozen, in groups of eight digits, and how a passkey or
 * security key binds to it. Closed by default, since it is a check and not a step.
 */
const props = defineProps<{offer: BatchStartResponse}>()

const {t} = useI18n()

const documents = computed(() => {
  const seen = new Map<string, {title: string; groups: string[]}>()
  for (const field of props.offer.fields) {
    if (seen.has(field.requestUid)) continue
    seen.set(field.requestUid, {
      title: field.documentTitle ?? field.documentMemberName,
      groups: field.contentSha256.match(new RegExp(`.{1,${DIGITS_PER_GROUP}}`, 'g')) ?? [],
    })
  }
  return [...seen.values()]
})
</script>

<template>
  <details class="text-sm" data-testid="signing-binding">
    <summary class="cursor-pointer text-(--text-muted) hover:text-(--text) transition-colors">
      {{ t('signing.binding.summary') }}
    </summary>
    <div class="mt-3 space-y-2">
      <MutedText tag="p" size="sm">{{ t('signing.binding.boundHint') }}</MutedText>
      <MutedText tag="p" size="sm">{{ t('signing.binding.hashHint') }}</MutedText>
      <div v-for="document in documents" :key="document.title">
        <p class="font-medium">{{ document.title }}</p>
        <p class="font-mono break-all">{{ document.groups.join(' ') }}</p>
      </div>
    </div>
  </details>
</template>
