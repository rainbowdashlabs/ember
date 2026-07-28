/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import MemberListView from '~/views/stationview/inventory/MemberListView.vue'

definePageMeta({
  layout: 'station',
  name: 'inventory-members',
})
</script>

<template>
  <MemberListView />
</template>
