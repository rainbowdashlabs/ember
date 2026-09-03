/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import DeviceUnlockView from '~/views/DeviceUnlockView.vue'

definePageMeta({
  layout: 'default',
  name: 'unlock-device',
})

useHead({
  title: 'Dieses Gerät freischalten',
})
</script>

<template>
  <DeviceUnlockView />
</template>
