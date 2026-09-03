/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import PasswordInput from '@/components/input/text/PasswordInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import Alert from '@/components/feedback/Alert.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'

const props = defineProps<{
  error: string
  loading: boolean
  registrationEnabled: boolean
  /** Whether the instance offers the passkey path and this browser can walk it. */
  passkeyAvailable?: boolean
}>()

const emit = defineEmits<{
  (e: 'submit'): void
  (e: 'passkey'): void
}>()

const identifier = defineModel<string>('identifier', {required: true})
const password = defineModel<string>('password', {required: true})
const trustedDevice = defineModel<boolean>('trustedDevice', {default: false})

const {t} = useI18n()
</script>

<template>
  <form class="space-y-4" @submit.prevent="emit('submit')">
    <Alert v-if="props.error" variant="error">{{ props.error }}</Alert>

    <div class="space-y-1">
      <FieldLabel>{{ t('login.identifier') }}</FieldLabel>
      <TextInput v-model="identifier"
                 :disabled="props.loading" :placeholder="t('login.identifier')"
                 autocomplete="username webauthn"/>
    </div>

    <div class="space-y-1">
      <FieldLabel>{{ t('login.password') }}</FieldLabel>
      <PasswordInput v-model="password"
                     :disabled="props.loading" :placeholder="t('login.password')"
                     autocomplete="current-password webauthn"/>
    </div>

    <label class="flex items-start gap-3">
      <ToggleInput v-model="trustedDevice" :aria-label="t('login.trustedDevice')" :disabled="props.loading"/>
      <span class="text-sm">
        {{ t('login.trustedDevice') }}
        <span class="block text-xs text-(--text-muted)">{{ t('login.trustedDeviceHint') }}</span>
      </span>
    </label>

    <PrimaryButton :disabled="props.loading || !identifier || !password" class="w-full" @click="emit('submit')">
      {{ props.loading ? t('common.loading') : t('login.submit') }}
    </PrimaryButton>

    <SecondaryButton v-if="props.passkeyAvailable" type="button" :disabled="props.loading" class="w-full"
                     :icon="['fas', 'fingerprint']" @click="emit('passkey')">
      {{ t('login.withPasskey') }}
    </SecondaryButton>
    <router-link v-if="props.passkeyAvailable"
                 class="block w-full text-center text-sm text-(--text-muted) hover:text-(--text) transition-colors"
                 to="/unlock-device">
      {{ t('login.passkeyElsewhere') }}
    </router-link>

    <router-link class="block w-full text-center text-sm text-(--text-muted) hover:text-(--text) transition-colors"
                 to="/forgot-password">
      {{ t('login.forgotPassword') }}
    </router-link>
    <router-link v-if="props.registrationEnabled" class="block w-full text-center text-sm text-primary hover:underline" to="/apply">
      {{ t('login.applyForStation') }}
    </router-link>
  </form>
</template>
