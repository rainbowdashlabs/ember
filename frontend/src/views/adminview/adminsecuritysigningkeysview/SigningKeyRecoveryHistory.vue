/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {SigningKeyRecoveryEntry} from '@/api/generated/schema'
import {formatDateTime} from '@/util/format'

/**
 * Every time signing keys were given up, newest first: when, by whom, and how many of each kind, so a
 * changed fingerprint on a station's public page can be traced to the day it changed.
 */
defineProps<{
  recoveries: SigningKeyRecoveryEntry[]
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3">
    <SubHeader>{{ t('adminSecurity.signingKeys.historyTitle') }}</SubHeader>
    <MutedText v-if="recoveries.length === 0" tag="p" size="sm">{{ t('adminSecurity.signingKeys.historyEmpty') }}</MutedText>
    <ul v-else class="space-y-2 text-sm">
      <li v-for="entry in recoveries" :key="entry.id">
        {{ t('adminSecurity.signingKeys.historyEntry', {
          date: formatDateTime(entry.recoveredAt),
          name: entry.recoveredBy ?? t('adminSecurity.signingKeys.historyDeletedAccount'),
          authorities: entry.authoritySerials.length,
          stationKeys: entry.stationKeySerials.length,
        }) }}
      </li>
    </ul>
  </NeutralContainer>
</template>
