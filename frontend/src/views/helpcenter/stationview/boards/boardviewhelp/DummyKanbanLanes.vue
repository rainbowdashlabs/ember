/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import DummyLaneOpen from '@/views/helpcenter/stationview/boards/boardviewhelp/DummyLaneOpen.vue'
import DummyLaneInProgress from '@/views/helpcenter/stationview/boards/boardviewhelp/DummyLaneInProgress.vue'
import DummyLaneDone from '@/views/helpcenter/stationview/boards/boardviewhelp/DummyLaneDone.vue'
</script>

<template>
  <div class="flex flex-col md:flex-row gap-4 pb-4" style="min-height: 200px">
    <DummyLaneOpen />
    <DummyLaneInProgress />
    <DummyLaneDone />
  </div>
</template>
