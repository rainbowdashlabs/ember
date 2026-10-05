/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
/**
 * Names the fixed authenticator code a development instance accepts, so a seeded account whose
 * secret nobody holds can still pass a second factor. Anywhere else it renders nothing.
 */
import {onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {getDemoStatus} from '@/api/demo'
import Alert from '@/components/feedback/Alert.vue'

const DEV_CODE = '000000'

const {t} = useI18n()
const development = ref(false)

onMounted(async () => {
  development.value = await getDemoStatus().then(status => status.dev, () => false)
})
</script>

<template>
  <Alert v-if="development" variant="info">{{ t('twoFactor.verify.devCode', {code: DEV_CODE}) }}</Alert>
</template>
