/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import Alert from '@/components/feedback/Alert.vue'
import {useEventRoutes} from '@/composables/useEventRoutes'

/**
 * Says above the editor which appointment a new one is copied from, with a way back to it, and what
 * the copy leaves behind, so nobody looks for the sign-ups of the original in it.
 */
const props = defineProps<{
  /** The original. */
  eventId: number
  /** What the original is called. */
  name: string
}>()

const {t} = useI18n()
const eventRoutes = useEventRoutes()

const original = computed(() => ({name: eventRoutes.detail, params: {id: props.eventId}}))
</script>

<template>
  <Alert variant="info" data-testid="copied-from-notice">
    <i18n-t keypath="events.copy.notice" tag="p">
      <template #name>
        <NuxtLink :to="original" class="underline">{{ name }}</NuxtLink>
      </template>
    </i18n-t>
    <p class="text-xs">{{ t('events.copy.leftBehind') }}</p>
  </Alert>
</template>
