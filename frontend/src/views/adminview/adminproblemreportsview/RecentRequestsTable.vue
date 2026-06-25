/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'

defineProps<{
  requests: any[]
}>()

const {t} = useI18n()
</script>

<template>
  <div class="border-t border-(--border) pt-2">
    <p class="text-xs font-semibold text-(--text-muted) mb-1">{{ t('problemReport.recentRequests') }}</p>
    <div class="overflow-x-auto">
      <table class="w-full text-xs font-mono border-collapse">
        <thead>
          <tr class="text-left text-(--text-muted)">
            <th class="px-2 py-1">{{ t('problemReport.reqTime') }}</th>
            <th class="px-2 py-1">{{ t('problemReport.reqMethod') }}</th>
            <th class="px-2 py-1">{{ t('problemReport.reqUrl') }}</th>
            <th class="px-2 py-1">{{ t('problemReport.reqStatus') }}</th>
            <th class="px-2 py-1">{{ t('problemReport.reqDuration') }}</th>
            <th class="px-2 py-1">{{ t('problemReport.reqError') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(req, i) in requests" :key="i" :class="req.error ? 'text-error' : ''">
            <td class="px-2 py-0.5">{{ req.timestamp?.substring(11, 19) }}</td>
            <td class="px-2 py-0.5">{{ req.method }}</td>
            <td class="px-2 py-0.5 break-all">{{ req.url }}</td>
            <td class="px-2 py-0.5">
              <ErrorBadge v-if="req.status && req.status >= 400">{{ req.status }}</ErrorBadge>
              <SuccessBadge v-else-if="req.status">{{ req.status }}</SuccessBadge>
              <span v-else>–</span>
            </td>
            <td class="px-2 py-0.5">{{ req.duration }}ms</td>
            <td class="px-2 py-0.5 break-all">{{ req.error ?? '' }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
