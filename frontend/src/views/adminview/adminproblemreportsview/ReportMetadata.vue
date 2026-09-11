/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import ReportMetadataGrid from '@/components/problem/ReportMetadataGrid.vue'
import type {ProblemReport} from '@/api/problemReports'

const props = defineProps<{
  report: ProblemReport
}>()

const {t} = useI18n()

const entries = computed(() => [
  {label: t('problemReport.page'), value: props.report.pageUrl},
  {label: t('problemReport.roles'), value: props.report.userRoles},
  {label: t('problemReport.browser'), value: props.report.browserInfo},
  {label: t('problemReport.screen'), value: props.report.screenSize},
].filter((entry): entry is {label: string; value: string} => !!entry.value))
</script>

<template>
  <ReportMetadataGrid :entries="entries"/>
</template>
