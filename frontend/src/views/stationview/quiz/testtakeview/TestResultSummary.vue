/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import SuccessContainer from '@/components/container/SuccessContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'

const { t } = useI18n()
const router = useRouter()
</script>

<template>
  <SuccessContainer>
    <div class="text-center space-y-4 py-4">
      <font-awesome-icon :icon="['fas', 'check-circle']" class="text-4xl text-success" />
      <SectionHeader>{{ t('quiz.attempt.submitted') }}</SectionHeader>
      <p class="text-sm text-(--text-muted)">{{ t('quiz.attempt.submittedInfo') }}</p>
      <PrimaryButton @click="router.push({ name: 'quiz-tests' })">
        {{ t('quiz.attempt.backToTest') }}
      </PrimaryButton>
    </div>
  </SuccessContainer>
</template>
