/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import SetPasswordView from '~/views/SetPasswordView'

definePageMeta({
  layout: 'default',
  name: 'set-password',
})

useHead({
  title: 'Passwort setzen',
})
</script>

<template>
  <SetPasswordView />
</template>
