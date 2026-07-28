/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import SetPasswordView from '~/views/SetPasswordView.vue'

definePageMeta({
  layout: 'default',
  name: 'reset-password',
})

useHead({
  title: 'Passwort zurücksetzen',
})
</script>

<template>
  <SetPasswordView />
</template>
