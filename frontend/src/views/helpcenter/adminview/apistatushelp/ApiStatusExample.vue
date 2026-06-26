/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import OverviewSummaryCards from './OverviewSummaryCards.vue'
import OverviewChartPlaceholders from './OverviewChartPlaceholders.vue'
import OverviewTabsRow from './OverviewTabsRow.vue'
import OverviewEndpointTable from './OverviewEndpointTable.vue'

const {t} = useI18n()
</script>

<template>
  <OverviewSummaryCards/>
  <OverviewChartPlaceholders/>
  <OverviewTabsRow/>
  <OverviewEndpointTable/>
</template>
