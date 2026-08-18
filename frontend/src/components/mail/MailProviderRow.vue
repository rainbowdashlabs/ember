/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import IconButton from '@/components/button/IconButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import MailProviderCredentialFields from '@/components/mail/MailProviderCredentialFields.vue'
import {needsServerAddress, RELAY_PROVIDER_NAMES} from '@/util/mailProviders'
import type {MailProvider} from '@/api/mailProviders'

/**
 * One provider of the list mail is tried through.
 *
 * Every relay asks for something different, and calls it something different: Brevo wants a login
 * address and an SMTP key, SendGrid only a key and no user at all, a plain server wants host, port
 * and a password. So the credential fields come from a component of their own, which carries each
 * provider's labels and explanations. Only a plain server needs the server fields, and only it
 * shows them.
 */
const entry = defineModel<MailProvider>({required: true})

const props = defineProps<{
  position: number
  isFirst: boolean
  isLast: boolean
  /** Whether this list is a station's, which also shows the provider to its members. */
  showDisplayFields?: boolean
  /** The address the test field starts with, usually the one of whoever is looking. */
  defaultRecipient?: string
}>()

const emit = defineEmits<{
  remove: []
  move: [direction: number]
  test: [recipient: string]
}>()

/**
 * Where a test mail goes. Prefilled with the address of whoever is looking, because that is the
 * usual answer, and editable because it often is not: whether a relay delivers is frequently a
 * question about a mailbox somewhere else.
 */
const recipient = ref(props.defaultRecipient ?? '')

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
    <div class="flex items-center justify-between gap-2 flex-wrap">
      <div class="flex items-center gap-2">
        <span class="font-medium">{{ t('mailChain.position', {position: props.position}) }}</span>
        <PrimaryBadge v-if="props.isFirst">{{ t('mailChain.firstBadge') }}</PrimaryBadge>
      </div>
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
        <FieldLabel>{{ t('mailChain.provider') }}</FieldLabel>
        <SelectInput v-model="entry.provider" :aria-label="t('mailChain.provider')">
          <option v-for="provider in PROVIDERS" :key="provider" :value="provider">
            {{ RELAY_PROVIDER_NAMES[provider] ?? t('mailChain.ownServer') }}
          </option>
        </SelectInput>
      </div>
      <div class="space-y-1">
        <FieldLabel>{{ t('mailChain.attempts') }}</FieldLabel>
        <NumberInput v-model="entry.attempts" :min="1" :max="10" :aria-label="t('mailChain.attempts')"/>
        <p class="text-xs text-(--text-muted)">{{ t('mailChain.attemptsHint') }}</p>
      </div>
    </div>

    <div v-if="needsServer" class="grid grid-cols-1 md:grid-cols-2 gap-3">
      <div class="space-y-1">
        <FieldLabel>{{ t('mailChain.host') }}</FieldLabel>
        <TextInput v-model="entry.smtpHost" :aria-label="t('mailChain.host')"/>
      </div>
      <div class="space-y-1">
        <FieldLabel>{{ t('mailChain.port') }}</FieldLabel>
        <NumberInput v-model="entry.smtpPort" :min="1" :max="65535" :aria-label="t('mailChain.port')"/>
      </div>
      <div class="flex items-center justify-between gap-3">
        <FieldLabel>{{ t('mailChain.ssl') }}</FieldLabel>
        <ToggleInput v-model="entry.smtpSsl" :aria-label="t('mailChain.ssl')"/>
      </div>
      <template v-if="isSmtp">
        <div class="space-y-1">
          <FieldLabel>{{ t('mailChain.user') }}</FieldLabel>
          <TextInput v-model="user" :aria-label="t('mailChain.user')"/>
        </div>
        <div class="space-y-1">
          <FieldLabel>{{ t('mailChain.password') }}</FieldLabel>
          <TextInput v-model="secret" type="password" :aria-label="t('mailChain.password')"/>
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
        <FieldLabel>{{ t('mailChain.senderAddress') }}</FieldLabel>
        <TextInput v-model="entry.senderAddress" type="email" :aria-label="t('mailChain.senderAddress')"/>
      </div>
      <div class="space-y-1">
        <FieldLabel>{{ t('mailChain.senderName') }}</FieldLabel>
        <TextInput v-model="entry.senderName" :aria-label="t('mailChain.senderName')"/>
      </div>
      <div class="space-y-1">
        <FieldLabel>{{ t('mailChain.dailyLimit') }}</FieldLabel>
        <NumberInput v-model="entry.dailySendLimit" :min="0" :max="1000000"
                     :aria-label="t('mailChain.dailyLimit')"/>
        <p class="text-xs text-(--text-muted)">{{ t('mailChain.dailyLimitHint') }}</p>
      </div>
    </div>

    <div v-if="props.showDisplayFields" class="grid grid-cols-1 md:grid-cols-2 gap-3">
      <div class="space-y-1">
        <FieldLabel>{{ t('mailChain.providerName') }}</FieldLabel>
        <TextInput v-model="entry.providerName" :aria-label="t('mailChain.providerName')"/>
      </div>
      <div class="space-y-1">
        <FieldLabel>{{ t('mailChain.providerUrl') }}</FieldLabel>
        <TextInput v-model="entry.providerUrl" :aria-label="t('mailChain.providerUrl')"/>
      </div>
    </div>

    <div class="flex items-end gap-2 flex-wrap">
      <div class="space-y-1 flex-1 min-w-56">
        <FieldLabel>{{ t('mailChain.testRecipient') }}</FieldLabel>
        <TextInput v-model="recipient" type="email" :aria-label="t('mailChain.testRecipient')"/>
      </div>
      <SecondaryButton :icon="['fas', 'paper-plane']" @click="emit('test', recipient)">
        {{ t('mailChain.test') }}
      </SecondaryButton>
    </div>
    <p class="text-xs text-(--text-muted)">{{ t('mailChain.testHint') }}</p>

    <slot name="webhook" :provider="entry.provider"/>
  </div>
</template>
