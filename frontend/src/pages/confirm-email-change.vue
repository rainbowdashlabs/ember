/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import ConfirmEmailChangeView from '~/views/ConfirmEmailChangeView.vue'

definePageMeta({
  layout: 'default',
  name: 'confirm-email-change',
})

const {t} = useI18n()

useHead({
  title: t('pageHead.confirmEmailChange'),
})
</script>

<template>
  <ConfirmEmailChangeView/>
</template>
