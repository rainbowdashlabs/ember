/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SuccessContainer from '@/components/container/SuccessContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'

defineEmits<{
  restart: []
}>()

const { t } = useI18n()
</script>

<template>
  <SuccessContainer>
    <div class="text-center space-y-4 py-4">
      <font-awesome-icon :icon="['fas', 'check']" class="text-4xl" />
      <SubHeader>{{ t('quiz.training.finished') }}</SubHeader>
      <div class="flex justify-center">
        <SecondaryButton :icon="['fas', 'rotate']" @click="$emit('restart')">
          {{ t('quiz.training.restart') }}
        </SecondaryButton>
      </div>
    </div>
  </SuccessContainer>
</template>
