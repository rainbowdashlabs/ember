/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SuccessContainer from '@/components/container/SuccessContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'

defineProps<{
  count: number
  leaveLabel: string
}>()

const emit = defineEmits<{
  leave: []
  startOver: []
}>()

const {t} = useI18n()
</script>

<template>
  <SuccessContainer class="space-y-4">
    <SubHeader>{{ t('quiz.csv.importSuccess', {count}) }}</SubHeader>
    <div class="flex gap-3">
      <PrimaryButton :icon="['fas', 'chevron-left']" @click="emit('leave')">{{ leaveLabel }}</PrimaryButton>
      <SecondaryButton :icon="['fas', 'rotate']" @click="emit('startOver')">
        {{ t('csvImport.startOver') }}
      </SecondaryButton>
    </div>
  </SuccessContainer>
</template>
