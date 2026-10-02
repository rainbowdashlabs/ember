/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import InstallView from '~/views/InstallView.vue'

definePageMeta({
  layout: 'default',
  name: 'install',
})

const {t} = useI18n()

useHead({
  title: t('pageHead.install.title'),
  meta: [
    {name: 'description', content: t('pageHead.install.description')},
  ],
})
</script>

<template>
  <InstallView/>
</template>
