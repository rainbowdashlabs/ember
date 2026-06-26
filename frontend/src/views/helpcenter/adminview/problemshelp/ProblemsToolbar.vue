/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import ErrorContainer from '@/components/container/ErrorContainer.vue'
import InfoContainer from '@/components/container/InfoContainer.vue'

const {t} = useI18n()
</script>

<template>
  <div class="flex items-center justify-between mb-4">
    <div class="flex items-center gap-2">
      <SelectionToggleButton :selected="false">
        {{ t('adminProblems.showAcknowledged') }}
      </SelectionToggleButton>
      <SecondaryButton :icon="['fas', 'check-double']">
        {{ t('adminProblems.acknowledgeAll') }}
      </SecondaryButton>
    </div>
  </div>

  <div class="flex gap-3 mb-4">
    <ErrorContainer class="flex items-center gap-2 !py-2 !px-3">
      <font-awesome-icon :icon="['fas', 'circle-xmark']"/>
      <span class="font-semibold">2</span> {{ t('adminProblems.errors') }}
    </ErrorContainer>
    <InfoContainer class="flex items-center gap-2 !py-2 !px-3">
      <font-awesome-icon :icon="['fas', 'triangle-exclamation']"/>
      <span class="font-semibold">1</span> {{ t('adminProblems.warnings') }}
    </InfoContainer>
  </div>
</template>
