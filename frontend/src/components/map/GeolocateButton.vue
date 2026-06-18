/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'

const emit = defineEmits<{
  (e: 'located', lat: number, lng: number): void
  (e: 'error', message: string): void
}>()

const {t} = useI18n()
const loading = ref(false)

async function locate() {
  if (typeof navigator === 'undefined' || !navigator.geolocation) {
    emit('error', t('geolocation.geolocationUnsupported'))
    return
  }
  loading.value = true
  navigator.geolocation.getCurrentPosition(
      (pos) => {
        loading.value = false
        emit('located', pos.coords.latitude, pos.coords.longitude)
      },
      (err) => {
        loading.value = false
        const message =
            err.code === err.PERMISSION_DENIED
                ? t('geolocation.geolocationDenied')
                : t('geolocation.geolocationFailed')
        emit('error', message)
      },
      {enableHighAccuracy: true, timeout: 10_000, maximumAge: 0},
  )
}
</script>

<template>
  <SecondaryButton :icon="['fas', 'location-dot']" :disabled="loading" @click="locate">
    {{ loading ? t('geolocation.locating') : t('geolocation.useMyPosition') }}
  </SecondaryButton>
</template>
