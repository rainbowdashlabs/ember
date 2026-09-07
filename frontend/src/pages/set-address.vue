/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import SetAddressView from '~/views/SetAddressView.vue'

definePageMeta({
  layout: 'default',
  name: 'set-address',
})

useHead({
  title: 'E-Mail-Adresse hinterlegen',
})
</script>

<template>
  <SetAddressView />
</template>
