/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SetAddressView from '~/views/SetAddressView.vue'

definePageMeta({
  layout: 'default',
  name: 'set-address',
})

const {t} = useI18n()

useHead({
  title: t('pageHead.setAddress'),
})
</script>

<template>
  <SetAddressView />
</template>
