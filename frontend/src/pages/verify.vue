/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SealVerificationView from '~/views/public/SealVerificationView.vue'
import {useCanonical} from '~/composables/useCanonical'
import {socialMeta} from '~/util/socialMeta'

definePageMeta({
  layout: 'default',
  name: 'public-seal-verification',
})

useCanonical('/verify')

const {t} = useI18n()

useHead({
  title: t('pageHead.sealVerification.title'),
  meta: socialMeta({
    title: t('pageHead.sealVerification.title'),
    description: t('pageHead.sealVerification.description'),
  }),
})
</script>

<template>
  <SealVerificationView />
</template>
