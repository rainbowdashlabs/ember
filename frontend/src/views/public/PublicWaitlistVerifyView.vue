/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute} from 'vue-router'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SuccessContainer from '@/components/container/SuccessContainer.vue'
import ErrorContainer from '@/components/container/ErrorContainer.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import {waitingList} from '@/api'

const {t} = useI18n()
const route = useRoute()

const loading = ref(true)
const success = ref(false)
const error = ref(false)

onMounted(async () => {
  const token = route.params.token as string
  if (!token) {
    error.value = true
    loading.value = false
    return
  }
  try {
    await waitingList.verifyPublicRegistration(token)
    success.value = true
  } catch {
    error.value = true
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="max-w-lg mx-auto mt-16 px-4">
    <Spinner v-if="loading" size="lg"/>

    <SuccessContainer v-if="success" class="space-y-3 text-center">
      <font-awesome-icon :icon="['fas', 'circle-check']" class="text-4xl text-success"/>
      <SectionHeader>{{ t('waitingList.publicRegistration.verifyTitle') }}</SectionHeader>
      <p>{{ t('waitingList.publicRegistration.verifyText') }}</p>
    </SuccessContainer>

    <ErrorContainer v-if="error" class="space-y-3 text-center">
      <font-awesome-icon :icon="['fas', 'circle-xmark']" class="text-4xl text-error"/>
      <SectionHeader>{{ t('common.error') }}</SectionHeader>
      <p>{{ t('waitingList.publicRegistration.verifyError') }}</p>
    </ErrorContainer>
  </div>
</template>
