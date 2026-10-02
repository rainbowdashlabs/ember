/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SetPasswordView from '~/views/SetPasswordView.vue'

definePageMeta({
  layout: 'default',
  name: 'reset-password',
})

const {t} = useI18n()

useHead({
  title: t('pageHead.resetPassword'),
})
</script>

<template>
  <SetPasswordView />
</template>
