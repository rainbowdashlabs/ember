/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import ConfirmEmailChangeView from '~/views/ConfirmEmailChangeView.vue'

definePageMeta({
  layout: 'default',
  name: 'confirm-email-change',
})

useHead({
  title: 'E-Mail-Adresse bestätigen',
})
</script>

<template>
  <ConfirmEmailChangeView/>
</template>
