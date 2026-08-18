/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import IconButton from '@/components/button/IconButton.vue'
import MailProviderCredentialFields from '@/components/mail/MailProviderCredentialFields.vue'
import {needsServerAddress, RELAY_PROVIDER_NAMES} from '@/util/mailProviders'
import type {MailFallback} from '@/api/mailFallbacks'

/**
 * One provider in a fallback chain.
 *
 * Every relay asks for something different, and calls it something different: Brevo wants a login
 * address and an SMTP key, SendGrid only a key and no user at all, a plain server wants host, port
 * and a password. So the credential fields come from the same component the primary configuration
 * uses, which carries each provider's own labels and explanations. Only a plain SMTP server needs
 * the server fields, and only it shows them.
 */
const entry = defineModel<MailFallback>({required: true})

const props = defineProps<{
  position: number
  isFirst: boolean
  isLast: boolean
}>()

const emit = defineEmits<{
  remove: []
  move: [direction: number]
}>()

const {t} = useI18n()

const PROVIDERS = ['SMTP', ...Object.keys(RELAY_PROVIDER_NAMES)]

const isSmtp = computed(() => entry.value.provider === 'SMTP')
const needsServer = computed(() => needsServerAddress(entry.value.provider))

/**
 * A plain server authenticates with its password, a relay with its key. The row keeps both fields
 * on the entry and shows whichever the chosen provider actually uses.
 */
const secret = computed({
  get: () => (isSmtp.value ? entry.value.smtpPassword : entry.value.apiKey),
  set: (value: string) => {
    if (isSmtp.value) entry.value.smtpPassword = value
    else entry.value.apiKey = value
  },
})

const user = computed({
  get: () => entry.value.smtpUser,
  set: (value: string) => (entry.value.smtpUser = value),
})
</script>

<template>
  <div class="space-y-3 border-t border-(--border) pt-4">
    <div class="flex items-center justify-between gap-2">
      <div class="font-medium">{{ t('mailFallbacks.position', {position: props.position}) }}</div>
      <div class="flex gap-1">
        <IconButton :icon="['fas', 'arrow-up']" :label="t('common.moveUp')" :disabled="props.isFirst"
                    @click="emit('move', -1)"/>
        <IconButton :icon="['fas', 'arrow-down']" :label="t('common.moveDown')" :disabled="props.isLast"
                    @click="emit('move', 1)"/>
        <IconButton :icon="['fas', 'trash']" :label="t('common.delete')" @click="emit('remove')"/>
      </div>
    </div>

    <div class="grid grid-cols-1 md:grid-cols-2 gap-3">
      <div class="space-y-1">
        <FieldLabel>{{ t('mailFallbacks.provider') }}</FieldLabel>
        <SelectInput v-model="entry.provider">
          <option v-for="provider in PROVIDERS" :key="provider" :value="provider">
            {{ RELAY_PROVIDER_NAMES[provider] ?? t('mailFallbacks.ownServer') }}
          </option>
        </SelectInput>
      </div>
      <div class="space-y-1">
        <FieldLabel>{{ t('mailFallbacks.attempts') }}</FieldLabel>
        <NumberInput v-model="entry.attempts" :min="1" :max="10"/>
        <p class="text-xs text-(--text-muted)">{{ t('mailFallbacks.attemptsHint') }}</p>
      </div>
    </div>

    <div v-if="needsServer" class="grid grid-cols-1 md:grid-cols-2 gap-3">
      <div class="space-y-1">
        <FieldLabel>{{ t('mailFallbacks.host') }}</FieldLabel>
        <TextInput v-model="entry.smtpHost"/>
      </div>
      <div class="space-y-1">
        <FieldLabel>{{ t('mailFallbacks.port') }}</FieldLabel>
        <NumberInput v-model="entry.smtpPort" :min="1" :max="65535"/>
      </div>
      <div class="flex items-center justify-between gap-3">
        <FieldLabel>{{ t('mailFallbacks.ssl') }}</FieldLabel>
        <ToggleInput v-model="entry.smtpSsl" :aria-label="t('mailFallbacks.ssl')"/>
      </div>
      <template v-if="isSmtp">
        <div class="space-y-1">
          <FieldLabel>{{ t('mailFallbacks.user') }}</FieldLabel>
          <TextInput v-model="user"/>
        </div>
        <div class="space-y-1">
          <FieldLabel>{{ t('mailFallbacks.password') }}</FieldLabel>
          <TextInput v-model="secret" type="password"/>
        </div>
      </template>
    </div>

    <MailProviderCredentialFields
        v-if="!isSmtp"
        v-model:user="user"
        v-model:secret="secret"
        :provider="entry.provider"
    />

    <div class="grid grid-cols-1 md:grid-cols-2 gap-3">
      <div class="space-y-1">
        <FieldLabel>{{ t('mailFallbacks.senderAddress') }}</FieldLabel>
        <TextInput v-model="entry.senderAddress" type="email"/>
      </div>
      <div class="space-y-1">
        <FieldLabel>{{ t('mailFallbacks.senderName') }}</FieldLabel>
        <TextInput v-model="entry.senderName"/>
      </div>
    </div>
  </div>
</template>
