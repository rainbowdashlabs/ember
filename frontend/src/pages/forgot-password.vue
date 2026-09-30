/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import ForgotPasswordView from '~/views/ForgotPasswordView.vue'

definePageMeta({
  layout: 'default',
  name: 'forgot-password',
})

const {t} = useI18n()

useHead({
  title: t('pageHead.forgotPassword'),
})
</script>

<template>
  <ForgotPasswordView />
</template>
