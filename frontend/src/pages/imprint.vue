/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import ImprintView from '~/views/ImprintView.vue'
import {useCanonical} from '~/composables/useCanonical'

definePageMeta({
  layout: 'default',
  name: 'imprint',
})

useCanonical('/imprint')

const {t} = useI18n()

useHead({
  title: t('pageHead.imprint.title'),
  meta: [
    {name: 'description', content: t('pageHead.imprint.description')},
    {property: 'og:title', content: t('pageHead.imprint.socialTitle')},
    {property: 'og:type', content: 'website'},
  ],
})
</script>

<template>
  <ImprintView />
</template>
