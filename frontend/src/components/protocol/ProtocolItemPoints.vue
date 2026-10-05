/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import type {TestProtocolItem} from '@/api/generated/schema'

/** What a protocol point is worth, marked as bonus where it adds to the score but not to the maximum. */
defineProps<{
  item: Pick<TestProtocolItem, 'points' | 'bonus'>
}>()

const {t} = useI18n()
</script>

<template>
  <span class="inline-flex items-center gap-1 shrink-0 text-xs text-[var(--text-muted)] font-mono">
    <InfoBadge v-if="item.bonus">{{ t('protocol.bonus') }}</InfoBadge>
    {{ item.bonus ? '+' : '' }}{{ item.points }}P
  </span>
</template>
