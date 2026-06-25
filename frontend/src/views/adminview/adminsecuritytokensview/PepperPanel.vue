/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import Alert from '@/components/feedback/Alert.vue'

const {t} = useI18n()

defineProps<{
  configured: boolean
  generating: boolean
}>()

defineEmits<{
  generate: []
}>()
</script>

<template>
  <NeutralContainer class="space-y-3">
    <SubHeader>{{ t('adminSecurity.tokens.pepper.title') }}</SubHeader>
    <MutedText tag="p" size="sm">{{ t('adminSecurity.tokens.pepper.hint') }}</MutedText>

    <div class="flex items-center justify-between gap-3">
      <div>
        <SuccessBadge v-if="configured">{{ t('adminSecurity.tokens.pepper.configured') }}</SuccessBadge>
        <ErrorBadge v-else>{{ t('adminSecurity.tokens.pepper.missing') }}</ErrorBadge>
      </div>
      <PrimaryButton v-if="!configured" :disabled="generating" @click="$emit('generate')">
        {{ generating ? t('common.loading') : t('adminSecurity.tokens.pepper.generate') }}
      </PrimaryButton>
    </div>

    <Alert variant="info">{{ t('adminSecurity.tokens.pepper.restartWarning') }}</Alert>
  </NeutralContainer>
</template>
