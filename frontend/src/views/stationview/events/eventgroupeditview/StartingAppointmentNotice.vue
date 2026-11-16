/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import Alert from '@/components/feedback/Alert.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'

/** Names the appointment a new event group starts from, with a way to create the group without it. */
defineProps<{
  name: string
}>()

const emit = defineEmits<{
  drop: []
}>()

const {t} = useI18n()
</script>

<template>
  <Alert variant="info">
    <div class="space-y-2">
      <p>{{ t('eventGroups.startingAppointment', {name}) }}</p>
      <SecondaryButton compact @click="emit('drop')">{{ t('eventGroups.dropStartingAppointment') }}</SecondaryButton>
    </div>
  </Alert>
</template>
