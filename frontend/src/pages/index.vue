/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import HomeView from '~/views/HomeView.vue'
import {useCanonical} from '~/composables/useCanonical'
import {useReturnToLastArea} from '~/composables/useReturnToLastArea'

useCanonical('/')
useReturnToLastArea()

const {t} = useI18n()

useHead({
  title: t('pageHead.home.title'),
  meta: [
    {name: 'description', content: t('pageHead.home.description')},
    {property: 'og:title', content: t('pageHead.home.socialTitle')},
    {property: 'og:description', content: t('pageHead.home.socialDescription')},
    {property: 'og:type', content: 'website'},
    {name: 'twitter:card', content: 'summary'},
    {name: 'twitter:title', content: t('pageHead.home.socialTitle')},
    {name: 'twitter:description', content: t('pageHead.home.socialDescription')},
  ],
  script: [
    {
      type: 'application/ld+json',
      innerHTML: JSON.stringify({
        '@context': 'https://schema.org',
        '@type': 'SoftwareApplication',
        name: 'Ember',
        applicationCategory: 'BusinessApplication',
        operatingSystem: 'Web',
        description: t('pageHead.home.title'),
        offers: {'@type': 'Offer', price: '0', priceCurrency: 'EUR'},
        author: {'@type': 'Organization', name: 'RainbowDashLabs'},
      }),
    },
  ],
})
</script>

<template>
  <HomeView />
</template>
