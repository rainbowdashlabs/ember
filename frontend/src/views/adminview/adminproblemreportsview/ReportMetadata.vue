/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import type {ProblemReport} from '@/api/problemReports'

defineProps<{
  report: ProblemReport
}>()

const {t} = useI18n()
</script>

<template>
  <div class="grid grid-cols-1 md:grid-cols-2 gap-2 text-xs border-t border-(--border) pt-2">
    <div v-if="report.pageUrl">
      <span class="font-semibold text-(--text-muted)">{{ t('problemReport.page') }}:</span>
      <span class="ml-1 break-all">{{ report.pageUrl }}</span>
    </div>
    <div v-if="report.userRoles">
      <span class="font-semibold text-(--text-muted)">{{ t('problemReport.roles') }}:</span>
      <span class="ml-1">{{ report.userRoles }}</span>
    </div>
    <div v-if="report.browserInfo">
      <span class="font-semibold text-(--text-muted)">{{ t('problemReport.browser') }}:</span>
      <span class="ml-1 break-all">{{ report.browserInfo }}</span>
    </div>
    <div v-if="report.screenSize">
      <span class="font-semibold text-(--text-muted)">{{ t('problemReport.screen') }}:</span>
      <span class="ml-1">{{ report.screenSize }}</span>
    </div>
  </div>
</template>
