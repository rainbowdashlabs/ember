/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import PasskeyOfferView from '~/views/PasskeyOfferView.vue'

definePageMeta({
  layout: 'default',
  name: 'passkey-offer',
})

useHead({
  title: 'Passkey einrichten',
})
</script>

<template>
  <PasskeyOfferView />
</template>
