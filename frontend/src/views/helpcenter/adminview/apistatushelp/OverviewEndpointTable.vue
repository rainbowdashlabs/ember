/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="overflow-x-auto">
    <table class="w-full text-sm">
      <thead>
        <tr class="text-left text-xs text-(--text-muted) border-b border-[var(--border)]">
          <th class="py-2 pr-3">{{ t('apiStatus.endpoint') }}</th>
          <th class="py-2 pr-3 text-right">{{ t('apiStatus.count') }}</th>
          <th class="py-2 pr-3 text-right">{{ t('apiStatus.avg') }}</th>
          <th class="py-2 pr-3 text-right">Min</th>
          <th class="py-2 pr-3 text-right">Max</th>
          <th class="py-2 text-right">{{ t('apiStatus.errorRate') }}</th>
        </tr>
      </thead>
      <tbody>
        <tr class="border-b border-[var(--border)] cursor-pointer hover:bg-[var(--bg-accent)]/10">
          <td class="py-2 pr-3 font-mono text-xs">
            <span class="font-semibold mr-1 text-green-600">GET</span>/api/v1/kb/search
          </td>
          <td class="py-2 pr-3 text-right tabular-nums">94</td>
          <td class="py-2 pr-3 text-right tabular-nums">210ms</td>
          <td class="py-2 pr-3 text-right tabular-nums text-(--text-muted)">45ms</td>
          <td class="py-2 pr-3 text-right tabular-nums text-(--text-muted)">2340ms</td>
          <td class="py-2 text-right tabular-nums">0.0%</td>
        </tr>
        <tr class="border-b border-[var(--border)] cursor-pointer hover:bg-[var(--bg-accent)]/10">
          <td class="py-2 pr-3 font-mono text-xs">
            <span class="font-semibold mr-1 text-blue-600">POST</span>/api/v1/quiz/submit
          </td>
          <td class="py-2 pr-3 text-right tabular-nums">382</td>
          <td class="py-2 pr-3 text-right tabular-nums">45ms</td>
          <td class="py-2 pr-3 text-right tabular-nums text-(--text-muted)">12ms</td>
          <td class="py-2 pr-3 text-right tabular-nums text-(--text-muted)">180ms</td>
          <td class="py-2 text-right tabular-nums">0.0%</td>
        </tr>
        <tr class="border-b border-[var(--border)] last:border-0 cursor-pointer hover:bg-[var(--bg-accent)]/10">
          <td class="py-2 pr-3 font-mono text-xs">
            <span class="font-semibold mr-1 text-blue-600">POST</span>/api/v1/mail/send
          </td>
          <td class="py-2 pr-3 text-right tabular-nums">3</td>
          <td class="py-2 pr-3 text-right tabular-nums">—</td>
          <td class="py-2 pr-3 text-right tabular-nums text-(--text-muted)">—</td>
          <td class="py-2 pr-3 text-right tabular-nums text-(--text-muted)">—</td>
          <td class="py-2 text-right tabular-nums text-red-500 font-semibold">100.0%</td>
        </tr>
      </tbody>
    </table>
  </NeutralContainer>
</template>
