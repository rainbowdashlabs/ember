/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import DummyBoardHeader from '@/views/helpcenter/stationview/boards/boardviewhelp/DummyBoardHeader.vue'
import DummyBoardFilters from '@/views/helpcenter/stationview/boards/boardviewhelp/DummyBoardFilters.vue'
import DummyKanbanLanes from '@/views/helpcenter/stationview/boards/boardviewhelp/DummyKanbanLanes.vue'

const {t} = useI18n()
</script>

<template>
  <div>
    <DummyBoardHeader />
    <DummyBoardFilters />
    <DummyKanbanLanes />
  </div>
</template>
