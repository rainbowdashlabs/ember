/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import PasskeyOfferView from '~/views/PasskeyOfferView.vue'

definePageMeta({
  layout: 'default',
  name: 'passkey-offer',
})

const {t} = useI18n()

useHead({
  title: t('pageHead.passkey'),
})
</script>

<template>
  <PasskeyOfferView />
</template>
