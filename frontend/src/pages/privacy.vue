/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import PrivacyPolicyView from '~/views/PrivacyPolicyView'

definePageMeta({
  layout: 'default',
  name: 'privacy',
})

useHead({
  title: 'Datenschutz',
})
</script>

<template>
  <PrivacyPolicyView />
</template>
