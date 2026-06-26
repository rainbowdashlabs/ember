/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { EvaluationResponse } from '@/api/protocol'

defineProps<{
  members: EvaluationResponse['members']
  totalMax: number
  totalAverage: string
  scoreColor: (score: number, max: number) => string
}>()

const { t } = useI18n()
</script>

<template>
  <tr class="font-bold text-sm">
    <td class="sticky left-0 z-10 bg-[var(--bg-accent)]  border border-[var(--border)] px-2 py-1.5">{{ t('protocol.total') }}</td>
    <td class="bg-[var(--bg-accent)] border border-[var(--border)] px-2 py-1.5 text-center font-mono">{{ totalMax }}</td>
    <td :class="['border border-[var(--border)] px-2 py-1.5 text-center font-mono', scoreColor(parseFloat(totalAverage), totalMax)]">{{ totalAverage }}</td>
    <td
      v-for="m in members"
      :key="m.memberId"
      :class="['border border-[var(--border)] px-2 py-1.5 text-center font-mono', scoreColor(m.totalScore, totalMax)]"
    >
      {{ m.totalScore }}
    </td>
  </tr>
</template>
