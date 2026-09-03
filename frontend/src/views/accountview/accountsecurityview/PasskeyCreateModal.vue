/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import Alert from '@/components/feedback/Alert.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import {usePasskeyCreation} from '@/composables/usePasskeyCreation'

/**
 * Creating a passkey from the security screen: name it, one sentence before the operating
 * system's own dialog, then the test drive. Closing after a creation tells the parent to
 * reload either way; only the trial's outcome differs.
 */
const {t} = useI18n()

const open = defineModel<boolean>({required: true})

const emit = defineEmits<{
  (e: 'created'): void
}>()

const label = ref('')
const {phase, error, start, runTrial, skipTrial, reset} = usePasskeyCreation()

watch(open, (v) => {
  if (v) {
    label.value = ''
    reset()
  }
})

async function create() {
  if (await start(label.value.trim() || t('passkeys.defaultLabel'))) emit('created')
}

function close() {
  open.value = false
}
</script>

<template>
  <Modal v-model="open" size="sm">
    <div class="space-y-4 p-4">
      <SubHeader>{{ t('passkeys.section.create') }}</SubHeader>
      <Alert v-if="error" variant="info">{{ error }}</Alert>

      <template v-if="phase === 'idle'">
        <MutedText tag="p" size="sm">{{ t('passkeys.explainer') }}</MutedText>
        <TextInput v-model="label" :placeholder="t('passkeys.section.labelPlaceholder')"/>
        <MutedText tag="p" size="sm">{{ t('passkeys.create.preparing') }}</MutedText>
        <div class="flex justify-between gap-2">
          <SecondaryButton type="button" @click="close">{{ t('common.cancel') }}</SecondaryButton>
          <PrimaryButton type="button" @click="create">{{ t('passkeys.offer.accept') }}</PrimaryButton>
        </div>
      </template>

      <p v-else-if="phase === 'creating'">{{ t('passkeys.create.preparing') }}</p>

      <template v-else-if="phase === 'trial' || phase === 'trialRunning'">
        <p>{{ t('passkeys.create.trialPrompt') }}</p>
        <div class="flex justify-between gap-2">
          <SecondaryButton type="button" :disabled="phase === 'trialRunning'" @click="skipTrial">
            {{ t('passkeys.create.trialSkip') }}
          </SecondaryButton>
          <PrimaryButton type="button" :disabled="phase === 'trialRunning'" @click="runTrial">
            {{ t('passkeys.create.trialRun') }}
          </PrimaryButton>
        </div>
      </template>

      <template v-else>
        <p>{{ phase === 'done' ? t('passkeys.create.trialOk') : t('passkeys.create.trialSkipped') }}</p>
        <div class="flex justify-end">
          <PrimaryButton type="button" @click="close">{{ t('passkeys.create.done') }}</PrimaryButton>
        </div>
      </template>
    </div>
  </Modal>
</template>
