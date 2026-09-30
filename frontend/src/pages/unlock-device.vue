/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import DeviceUnlockView from '~/views/DeviceUnlockView.vue'

definePageMeta({
  layout: 'default',
  name: 'unlock-device',
})

const {t} = useI18n()

useHead({
  title: t('pageHead.unlockDevice'),
})
</script>

<template>
  <DeviceUnlockView />
</template>
