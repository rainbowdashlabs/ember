/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import LoginView from '~/views/LoginView.vue'
import {socialMeta} from '~/util/socialMeta'

useHead({
  title: 'Anmelden',
  meta: socialMeta({
    title: 'Anmelden',
    description: 'Melde dich bei Ember an, der freien Plattform für die Verwaltung von Jugendfeuerwehren.',
  }),
})
</script>

<template>
  <LoginView />
</template>
