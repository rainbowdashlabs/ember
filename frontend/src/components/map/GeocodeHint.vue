/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import Alert from '@/components/feedback/Alert.vue'
import type {GeocodeOutcome} from '@/composables/useGeocode'

/**
 * What the reader should do after a lookup that was not exact: drag the pin onto the building, or
 * place it by hand. An exact match and no lookup at all say nothing.
 */
const props = defineProps<{
  /** How the last lookup came out, or null before the first one. */
  outcome: GeocodeOutcome | null
}>()

const {t} = useI18n()

const message = computed(() => {
  switch (props.outcome) {
    case 'STREET':
      return t('geocoding.hintStreet')
    case 'AREA':
      return t('geocoding.hintAreaStation')
    case 'NOT_FOUND':
      return t('geocoding.hintNotFound')
    default:
      return ''
  }
})
</script>

<template>
  <Alert v-if="message" variant="info" data-testid="geocode-hint">{{ message }}</Alert>
</template>
