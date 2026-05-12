/*
*     SPDX-License-Identifier: AGPL-3.0-only
*
*     Copyright (C) RainbowDashLabs and Contributor
*/
<script lang="ts" setup>
import {onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute} from 'vue-router'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import {stationApplications} from '@/api'

const {t} = useI18n()
const route = useRoute()

const loading = ref(true)
const success = ref(false)
const error = ref(false)

onMounted(async () => {
  const token = route.query.token as string
  if (!token) {
    error.value = true
    loading.value = false
    return
  }
  try {
    await stationApplications.verify(token)
    success.value = true
  } catch {
    error.value = true
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="flex items-center justify-center px-4 py-12">
    <div class="w-full max-w-md space-y-6 text-center">
      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="success" variant="success">{{ t('applyVerify.success') }}</Alert>
      <Alert v-if="error" variant="error">{{ t('applyVerify.error') }}</Alert>
      <router-link class="inline-block text-sm text-primary hover:underline" to="/login">{{
          t('apply.backToLogin')
        }}
      </router-link>
    </div>
  </div>
</template>
