/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import TermsOfServiceView from '~/views/TermsOfServiceView.vue'

definePageMeta({
  layout: 'default',
  name: 'terms',
})

const {t} = useI18n()

useHead({
  title: t('pageHead.terms'),
})
</script>

<template>
  <TermsOfServiceView />
</template>
