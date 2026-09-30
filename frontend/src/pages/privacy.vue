/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import PrivacyPolicyView from '~/views/PrivacyPolicyView.vue'
import {useCanonical} from '~/composables/useCanonical'

definePageMeta({
  layout: 'default',
  name: 'privacy',
})

useCanonical('/privacy')

const {t} = useI18n()

useHead({
  title: t('pageHead.privacy.title'),
  meta: [
    {name: 'description', content: t('pageHead.privacy.description')},
    {property: 'og:title', content: t('pageHead.privacy.socialTitle')},
    {property: 'og:type', content: 'website'},
  ],
})
</script>

<template>
  <PrivacyPolicyView />
</template>
