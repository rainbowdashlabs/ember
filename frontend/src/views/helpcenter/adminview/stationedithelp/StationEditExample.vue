/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import StationEditReadyForm from './StationEditReadyForm.vue'
import StationEditPendingForm from './StationEditPendingForm.vue'

const {t} = useI18n()
</script>

<template>
  <SecondaryButton :icon="['fas', 'chevron-left']" class="mb-4">
    {{ t('adminStations.back') }}
  </SecondaryButton>

  <StationEditReadyForm/>
  <StationEditPendingForm/>
</template>
