/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import VChart from 'vue-echarts'
import type {EChartsCoreOption} from 'echarts/core'
import NeutralContainer from '@/components/container/NeutralContainer.vue'

const props = defineProps<{
  groupPieOption: EChartsCoreOption
  sessionsLineOption: EChartsCoreOption
  attendanceBarOption: EChartsCoreOption
  inventoryBarOption: EChartsCoreOption
  registrationBarOption: EChartsCoreOption
  showRegistrations: boolean
}>()
</script>

<template>
  <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
    <NeutralContainer>
      <VChart :option="props.groupPieOption" autoresize style="height: 300px"/>
    </NeutralContainer>

    <NeutralContainer>
      <VChart :option="props.sessionsLineOption" autoresize style="height: 300px"/>
    </NeutralContainer>

    <NeutralContainer class="lg:col-span-2">
      <VChart :option="props.attendanceBarOption" autoresize style="height: 350px"/>
    </NeutralContainer>

    <NeutralContainer>
      <VChart :option="props.inventoryBarOption" autoresize style="height: 300px"/>
    </NeutralContainer>

    <NeutralContainer v-if="props.showRegistrations">
      <VChart :option="props.registrationBarOption" autoresize style="height: 300px"/>
    </NeutralContainer>
  </div>
</template>
