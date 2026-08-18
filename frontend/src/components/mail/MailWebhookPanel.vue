/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import Alert from '@/components/feedback/Alert.vue'
import {RELAY_PROVIDER_NAMES} from '@/util/mailProviders'

/**
 * The address a mail provider reports delivery events to, with the steps for the provider that is
 * actually configured.
 *
 * The instructions sit here rather than only in the help centre because this is where somebody is
 * when they need them, and because they differ per provider: what is under "Transactional" at one
 * is under "Mail Settings" at the next, and one provider cannot report back at all. Saying that
 * plainly is more useful than a generic sentence that fits nobody.
 */
const props = defineProps<{
  url?: string
  /** The provider currently configured, so the steps match what the reader actually uses. */
  provider?: string
  /** Replaces the key, where the caller may. Absent hides the button. */
  regenerate?: () => Promise<string>
  /**
   * Stores the signing secret the provider issued. Absent hides the field, which is right for a
   * provider that does not sign its reports.
   */
  saveSigningSecret?: (secret: string) => Promise<unknown>
  /** Whether one is already stored. The secret itself never leaves the server. */
  signingSecretSet?: boolean
}>()

const {t, te} = useI18n()

const current = ref(props.url ?? '')
const copied = ref(false)
const replacing = ref(false)
const error = ref('')

watch(() => props.url, value => {
  if (value) current.value = value
})

const providerName = computed(() => RELAY_PROVIDER_NAMES[props.provider ?? ''] ?? '')

const steps = computed(() => {
  const out: string[] = []
  for (let i = 1; i <= 5; i++) {
    const key = `mailProviders.${props.provider}.webhookStep${i}`
    if (!te(key)) break
    out.push(t(key))
  }
  return out
})

const docsLink = computed(() => {
  const key = `mailProviders.${props.provider}.webhookLink`
  return te(key) ? t(key) : ''
})

/**
 * The provider's own settings page, where one is known. Saves hunting through a dashboard for the
 * place the address has to go.
 */
const settingsLink = computed(() => {
  const key = `mailProviders.${props.provider}.webhookAppLink`
  return te(key) ? t(key) : ''
})

async function copy() {
  try {
    await navigator.clipboard.writeText(current.value)
    copied.value = true
    setTimeout(() => (copied.value = false), 2000)
  } catch {
    error.value = t('mailWebhook.copyFailed')
  }
}

const signingSecret = ref('')

async function saveSecret() {
  if (!props.saveSigningSecret) return
  await props.saveSigningSecret(signingSecret.value)
  signingSecret.value = ''
}

async function replace() {
  if (!props.regenerate) return
  error.value = ''
  replacing.value = true
  try {
    current.value = await props.regenerate()
  } catch {
    error.value = t('common.error')
  } finally {
    replacing.value = false
  }
}
</script>

<template>
  <NeutralContainer class="space-y-3">
    <SectionHeader>{{ t('mailWebhook.title') }}</SectionHeader>
    <MutedText tag="p" size="sm">{{ t('mailWebhook.intro') }}</MutedText>
    <MutedText tag="p" size="sm">{{ t('mailWebhook.purpose') }}</MutedText>

    <Alert v-if="error" variant="error">{{ error }}</Alert>
    <Alert v-if="copied" variant="success">{{ t('mailWebhook.copied') }}</Alert>

    <code data-testid="mail-webhook-url"
          class="block w-full overflow-x-auto rounded-theme bg-(--bg-accent) px-3 py-2 text-sm select-all">
      {{ current || t('mailWebhook.unavailable') }}
    </code>

    <div class="flex flex-wrap gap-2">
      <SecondaryButton :icon="['fas', 'copy']" :disabled="!current" @click="copy">
        {{ t('mailWebhook.copy') }}
      </SecondaryButton>
      <SecondaryButton v-if="props.regenerate" :icon="['fas', 'rotate']" :disabled="replacing" @click="replace">
        {{ t('mailWebhook.replace') }}
      </SecondaryButton>
    </div>

    <div v-if="!providerName" class="text-sm text-(--text-muted)">{{ t('mailWebhook.noProvider') }}</div>
    <template v-else>
      <div v-if="steps.length" class="space-y-2 border-t border-(--border) pt-3">
        <div class="font-medium text-sm">{{ t('mailWebhook.stepsTitle', {provider: providerName}) }}</div>
        <ol class="list-decimal ml-5 space-y-1 text-sm">
          <li v-for="(step, i) in steps" :key="i">{{ step }}</li>
        </ol>
        <div class="flex flex-wrap gap-4">
          <a v-if="settingsLink" :href="settingsLink" target="_blank" rel="noopener"
             class="text-sm text-(--primary) underline">{{ t('mailWebhook.openSettings') }}</a>
          <a v-if="docsLink" :href="docsLink" target="_blank" rel="noopener"
             class="text-sm text-(--primary) underline">{{ t('mailWebhook.docs') }}</a>
        </div>
      </div>
      <Alert v-else variant="info">{{ t('mailWebhook.unsupported', {provider: providerName}) }}</Alert>

      <div v-if="props.saveSigningSecret" class="space-y-1 border-t border-(--border) pt-3">
        <FieldLabel>{{ t('mailWebhook.signingSecret') }}</FieldLabel>
        <TextInput v-model="signingSecret" type="password"
                   :placeholder="props.signingSecretSet ? t('mailWebhook.signingSecretStored') : ''"/>
        <MutedText tag="p" size="sm">{{ t('mailWebhook.signingSecretHint', {provider: providerName}) }}</MutedText>
        <SaveButton :action="saveSecret"/>
      </div>
    </template>

    <MutedText v-if="props.regenerate" tag="p" size="sm">{{ t('mailWebhook.replaceHint') }}</MutedText>
  </NeutralContainer>
</template>
