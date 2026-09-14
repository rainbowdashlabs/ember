/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SuccessContainer from '@/components/container/SuccessContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'

defineProps<{
  /** Whether a link is on its way that the registration is only complete after. */
  confirmedByMail: boolean
}>()

const {t} = useI18n()
</script>

<template>
  <SuccessContainer v-if="confirmedByMail" class="space-y-3">
    <SubHeader>{{ t('waitingList.publicRegistration.successTitle') }}</SubHeader>
    <p>{{ t('waitingList.publicRegistration.successText') }}</p>
    <p class="text-sm text-(--text-muted)">{{ t('waitingList.publicRegistration.successHint') }}</p>
  </SuccessContainer>
  <SuccessContainer v-else class="space-y-3">
    <SubHeader>{{ t('waitingList.publicRegistration.receivedTitle') }}</SubHeader>
    <p>{{ t('waitingList.publicRegistration.receivedText') }}</p>
  </SuccessContainer>
</template>
