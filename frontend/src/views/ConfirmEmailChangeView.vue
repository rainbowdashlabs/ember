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
import {auth} from '@/api'
import {EmailChangeStatus} from '@/api/auth'

/**
 * Where the link in a change-of-address mail lands.
 *
 * Both addresses have to confirm, so the first click is not the end of it. The page says which of
 * the two happened rather than reporting success either way, because somebody who stops reading
 * after the first mail would otherwise believe the address had already changed.
 */
const {t} = useI18n()
const route = useRoute()

const loading = ref(true)
const committed = ref(false)
const waiting = ref(false)
const error = ref(false)

onMounted(async () => {
  const token = route.query.token as string
  if (!token) {
    error.value = true
    loading.value = false
    return
  }
  try {
    const result = await auth.confirmEmailChange({token})
    if (result.status === EmailChangeStatus.COMMITTED) committed.value = true
    else waiting.value = true
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
      <Alert v-if="committed" variant="success">{{ t('confirmEmailChange.committed') }}</Alert>
      <Alert v-if="waiting" variant="info">{{ t('confirmEmailChange.waiting') }}</Alert>
      <Alert v-if="error" variant="error">{{ t('confirmEmailChange.error') }}</Alert>
      <router-link class="inline-block text-sm text-primary hover:underline" to="/login">
        {{ t('confirmEmailChange.backToLogin') }}
      </router-link>
    </div>
  </div>
</template>
