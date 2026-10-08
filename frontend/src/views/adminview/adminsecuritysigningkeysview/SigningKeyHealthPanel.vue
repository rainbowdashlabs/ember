/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import ErrorContainer from '@/components/container/ErrorContainer.vue'
import SuccessContainer from '@/components/container/SuccessContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import LockedKeyRow from './LockedKeyRow.vue'
import type {SigningKeyStatus} from '@/api/generated/schema'

/**
 * Whether the signing keys still open, and where they do not, which ones and the way out.
 *
 * <p>The way out is offered last, below the advice to restore the key file instead: giving the keys up
 * cannot be undone, restoring the file loses nothing.
 */
defineProps<{
  status: SigningKeyStatus
}>()

defineEmits<{recover: []}>()

const {t} = useI18n()
</script>

<template>
  <SuccessContainer v-if="status.locked.length === 0" data-testid="signing-keys-open">
    <p v-if="status.openKeys > 0">{{ t('adminSecurity.signingKeys.allOpen', {count: status.openKeys}) }}</p>
    <p v-else>{{ t('adminSecurity.signingKeys.noneYet') }}</p>
  </SuccessContainer>
  <ErrorContainer v-else class="space-y-3" data-testid="signing-keys-locked">
    <SubHeader>{{ t('adminSecurity.signingKeys.lockedTitle') }}</SubHeader>
    <p class="text-sm">{{ t('adminSecurity.signingKeys.lockedText') }}</p>
    <ul class="space-y-4">
      <LockedKeyRow v-for="key in status.locked" :key="key.serialNumber" :signing-key="key"/>
    </ul>
    <MutedText tag="p" size="sm">{{ t('adminSecurity.signingKeys.restoreFirst') }}</MutedText>
    <ErrorButton :icon="['fas', 'key']" data-testid="signing-keys-recover" @click="$emit('recover')">
      {{ t('adminSecurity.signingKeys.recover') }}
    </ErrorButton>
  </ErrorContainer>
</template>
