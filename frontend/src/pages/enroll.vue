/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import PasskeyEnrollView from '~/views/PasskeyEnrollView.vue'

definePageMeta({
  layout: 'default',
  name: 'enroll',
})

const {t} = useI18n()

useHead({
  title: t('pageHead.passkey'),
})
</script>

<template>
  <PasskeyEnrollView />
</template>
