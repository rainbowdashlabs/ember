/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import ApplyView from '~/views/ApplyView.vue'

definePageMeta({
  layout: 'default',
  name: 'apply',
})

const {t} = useI18n()

useHead({
  title: t('pageHead.apply'),
})
</script>

<template>
  <ApplyView />
</template>
