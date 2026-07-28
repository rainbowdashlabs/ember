/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import ForgotPasswordView from '~/views/ForgotPasswordView.vue'

definePageMeta({
  layout: 'default',
  name: 'forgot-password',
})

useHead({
  title: 'Passwort vergessen',
})
</script>

<template>
  <ForgotPasswordView />
</template>
