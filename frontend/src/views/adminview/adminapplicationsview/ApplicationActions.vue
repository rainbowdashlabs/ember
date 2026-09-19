/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {ApplicationStatus, type StationApplication} from '@/api/stationApplications'

/** What can be done with one application: a waiting one is accepted or denied, a denied one says why. */
defineProps<{
  app: StationApplication
  processing: boolean
}>()

const emit = defineEmits<{
  accept: [app: StationApplication]
  deny: [app: StationApplication]
}>()

const {t} = useI18n()
</script>

<template>
  <ButtonRow v-if="app.status === ApplicationStatus.PENDING" pair align="end">
    <PrimaryButton :disabled="processing" @click="emit('accept', app)">
      {{ t('adminApplications.accept') }}
    </PrimaryButton>
    <ErrorButton :disabled="processing" @click="emit('deny', app)">
      {{ t('adminApplications.deny') }}
    </ErrorButton>
  </ButtonRow>
  <MutedText v-else-if="app.status === ApplicationStatus.DENIED && app.denyReason" tag="div">
    {{ app.denyReason }}
  </MutedText>
</template>
