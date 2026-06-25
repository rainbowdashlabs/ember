/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SuccessContainer from '@/components/container/SuccessContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'

const {t} = useI18n()
</script>

<template>
  <SuccessContainer class="space-y-3">
    <SubHeader>{{ t('waitingList.publicRegistration.successTitle') }}</SubHeader>
    <p>{{ t('waitingList.publicRegistration.successText') }}</p>
    <p class="text-sm text-(--text-muted)">{{ t('waitingList.publicRegistration.successHint') }}</p>
  </SuccessContainer>
</template>
