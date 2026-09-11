/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import AdminBeaconView from '~/views/adminview/AdminBeaconView.vue'

definePageMeta({
  layout: 'admin',
  name: 'admin-beacon-faults',
})
</script>

<template>
  <AdminBeaconView section="faults" />
</template>
