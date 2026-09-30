/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import TwoFactorVerifyView from '~/views/TwoFactorVerifyView.vue'

definePageMeta({
  layout: 'default',
  name: '2fa-verify',
})

const {t} = useI18n()

useHead({
  title: t('pageHead.twoFactorVerify'),
})
</script>

<template>
  <TwoFactorVerifyView />
</template>
