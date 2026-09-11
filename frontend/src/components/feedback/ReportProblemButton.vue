/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import {openProblemReport} from '@/util/problemReportState'

/**
 * The way in to reporting a fault from anywhere, for somebody who noticed one without a request having
 * failed in front of them.
 *
 * <p>The form itself is rendered once by the layout, so this only asks for it. That is what lets a
 * failed request offer the same form with what went wrong already filled in.
 */
const {t} = useI18n()
</script>

<template>
  <button
      type="button"
      class="fixed bottom-4 right-4 z-40 h-10 w-10 rounded-full bg-error text-error-text shadow-lg hover:bg-error/80 transition-colors flex items-center justify-center"
      :title="t('problemReport.button')"
      data-testid="report-problem"
      @click="openProblemReport()"
  >
    <font-awesome-icon :icon="['fas', 'bug']" class="h-4 w-4"/>
  </button>
</template>
