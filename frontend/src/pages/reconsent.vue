/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import ReconsentView from '~/views/ReconsentView.vue'

definePageMeta({
  layout: 'default',
  name: 'reconsent',
})

const {t} = useI18n()

useHead({
  title: t('pageHead.reconsent'),
})
</script>

<template>
  <ReconsentView />
</template>
