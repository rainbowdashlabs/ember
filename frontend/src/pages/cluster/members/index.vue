/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import ClusterMemberManagementView from '~/views/clusterview/ClusterMemberManagementView.vue'

definePageMeta({
  layout: 'cluster',
  name: 'cluster-members',
})
</script>

<template>
  <ClusterMemberManagementView />
</template>
