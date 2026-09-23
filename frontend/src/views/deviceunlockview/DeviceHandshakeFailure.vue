/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import Alert from '@/components/feedback/Alert.vue'
import MutedText from '@/components/typography/MutedText.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'

/**
 * Where the handshake ended without a session, whatever ended it.
 *
 * <p>Every one of them leaves the reader in the same place: nothing happened, and the way on is to
 * ask again. Only the sentence differs, so only the sentence is a prop.
 */
const {t} = useI18n()

defineProps<{
  message: string
  /** A second line where the first needs one, such as saying why a request cannot be resumed. */
  hint?: string
  testId?: string
}>()

const emit = defineEmits<{
  retry: []
}>()
</script>

<template>
  <div class="space-y-4">
    <Alert :data-testid="testId" variant="error">{{ message }}</Alert>
    <MutedText v-if="hint" tag="p" size="sm">{{ hint }}</MutedText>
    <PrimaryButton class="w-full" @click="emit('retry')">{{ t('passkeys.device.retry') }}</PrimaryButton>
    <router-link class="block text-sm text-(--text-muted) hover:text-(--text)" to="/login">
      {{ t('passkeys.device.backToLogin') }}
    </router-link>
  </div>
</template>
