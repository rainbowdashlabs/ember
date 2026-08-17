/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import PrivacyPolicyView from '~/views/PrivacyPolicyView.vue'
import {useCanonical} from '~/composables/useCanonical'

definePageMeta({
  layout: 'default',
  name: 'privacy',
})

useCanonical('/privacy')

useHead({
  title: 'Datenschutz',
  meta: [
    {name: 'description', content: 'Datenschutzerklärung für die Nutzung von Ember.'},
    {property: 'og:title', content: 'Datenschutz - Ember'},
    {property: 'og:type', content: 'website'},
  ],
})
</script>

<template>
  <PrivacyPolicyView />
</template>
