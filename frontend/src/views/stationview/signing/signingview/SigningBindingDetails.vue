/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import MutedText from '@/components/typography/MutedText.vue'
import type {SigningStartResponse} from '@/api/generated/schema'

/** How many hexadecimal digits a group of the document's hash holds, as it is read off by eye. */
const DIGITS_PER_GROUP = 8

/**
 * What the signature binds to, for a signer who wants to compare it later: the SHA-256 of the document
 * as it was frozen, in groups of eight digits. Closed by default, since it is a check and not a step.
 */
const props = defineProps<{offer: SigningStartResponse}>()

const {t} = useI18n()

const hashGroups = computed(() => props.offer.contentSha256.match(new RegExp(`.{1,${DIGITS_PER_GROUP}}`, 'g')) ?? [])
</script>

<template>
  <details class="text-sm" data-testid="signing-binding">
    <summary class="cursor-pointer text-(--text-muted) hover:text-(--text) transition-colors">
      {{ t('signing.binding.summary') }}
    </summary>
    <div class="mt-3 space-y-2">
      <MutedText tag="p" size="sm">{{ t('signing.binding.hashHint') }}</MutedText>
      <p class="font-mono break-all">{{ hashGroups.join(' ') }}</p>
    </div>
  </details>
</template>
