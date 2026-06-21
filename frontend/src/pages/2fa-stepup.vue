/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import StepUpVerifyView from '~/views/StepUpVerifyView'

definePageMeta({
  layout: 'default',
  name: '2fa-stepup',
})

useHead({
  title: 'Sicherheitsbestätigung',
})
</script>

<template>
  <StepUpVerifyView />
</template>
