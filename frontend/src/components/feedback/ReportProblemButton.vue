/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import {openProblemReport} from '@/util/problemReportState'
import DockButton from '@/components/button/DockButton.vue'

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
  <DockButton :label="t('problemReport.button')" :icon="['fas', 'bug']" tone="error" data-testid="report-problem"
              @click="openProblemReport()"/>
</template>
