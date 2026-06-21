/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import TwoFactorVerifyView from '~/views/TwoFactorVerifyView'

definePageMeta({
  layout: 'default',
  name: '2fa-verify',
})

useHead({
  title: 'Zwei-Faktor-Authentifizierung',
})
</script>

<template>
  <TwoFactorVerifyView />
</template>
