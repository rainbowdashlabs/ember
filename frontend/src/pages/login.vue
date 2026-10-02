/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import LoginView from '~/views/LoginView.vue'
import {socialMeta} from '~/util/socialMeta'

const {t} = useI18n()

useHead({
  title: t('pageHead.login.title'),
  meta: socialMeta({
    title: t('pageHead.login.title'),
    description: t('pageHead.login.description'),
  }),
})
</script>

<template>
  <LoginView />
</template>
