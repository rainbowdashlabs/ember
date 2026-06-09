/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import TermsOfServiceView from '~/views/TermsOfServiceView'

definePageMeta({
  layout: 'default',
  name: 'terms',
})

useHead({
  title: 'Nutzungsbedingungen',
})
</script>

<template>
  <TermsOfServiceView />
</template>
