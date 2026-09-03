/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import MutedText from '@/components/typography/MutedText.vue'
import IconButton from '@/components/button/IconButton.vue'
import type {PasskeyEntry} from '@/api/passkeys'
import {providerName} from '@/util/aaguid'
import {formatDate} from '@/util/format'

/**
 * The member's passkeys, told apart by the name they gave each and the provider the AAGUID
 * resolves to. An entry that was never used and is older than a month says so quietly: that is
 * usually a passkey created on a device the member no longer has.
 */
const {t} = useI18n()

defineProps<{passkeys: PasskeyEntry[]}>()

const emit = defineEmits<{
  (e: 'rename', entry: PasskeyEntry): void
  (e: 'remove', entry: PasskeyEntry): void
}>()

const A_MONTH = 1000 * 60 * 60 * 24 * 30

function provider(entry: PasskeyEntry): string | null {
  return providerName(entry.aaguid)
}

function staleUnused(entry: PasskeyEntry): boolean {
  return !entry.lastUsedAt && Date.now() - new Date(entry.createdAt).getTime() > A_MONTH
}
</script>

<template>
  <ul class="divide-y divide-(--border)">
    <li v-for="entry in passkeys" :key="entry.id" class="flex items-center justify-between gap-3 py-3">
      <div class="min-w-0">
        <div class="font-medium">
          {{ entry.label }}
          <MutedText v-if="provider(entry)" tag="span" size="sm"> · {{ provider(entry) }}</MutedText>
        </div>
        <MutedText tag="div" size="sm">
          {{ t('passkeys.section.createdAt', {date: formatDate(entry.createdAt)}) }}
          <template v-if="entry.lastUsedAt">
            · {{ t('passkeys.section.lastUsedAt', {date: formatDate(entry.lastUsedAt)}) }}
          </template>
          <template v-else> · {{ t('passkeys.section.notTried') }}</template>
        </MutedText>
        <MutedText v-if="staleUnused(entry)" tag="div" size="sm">
          {{ t('passkeys.section.staleUnused') }}
        </MutedText>
      </div>
      <div class="flex shrink-0 gap-1">
        <IconButton :icon="['fas', 'pen']" :label="t('passkeys.section.rename')" @click="emit('rename', entry)"/>
        <IconButton :icon="['fas', 'trash']" :label="t('passkeys.section.remove')" @click="emit('remove', entry)"/>
      </div>
    </li>
  </ul>
</template>
