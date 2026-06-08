/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import BoardListView from '~/views/stationview/boards/BoardListView'

definePageMeta({
  layout: 'station',
  name: 'board-manage',
})
</script>

<template>
  <BoardListView />
</template>
