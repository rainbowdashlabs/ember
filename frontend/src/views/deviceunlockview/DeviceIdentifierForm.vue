/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'

/**
 * Who is asking to be let in.
 *
 * <p>Named here rather than at the approval, because a code raised for one person must not be
 * answerable by another. What is typed is a claim and never a proof: an address is public, and the
 * screen that follows looks identical whether or not it belongs to anybody, so this cannot be used
 * to find out who has an account here.
 */
const {t} = useI18n()

defineProps<{
  busy?: boolean
}>()

const emit = defineEmits<{
  submit: [identifier: string]
}>()

const identifier = ref('')

function submit() {
  const typed = identifier.value.trim()
  if (typed) emit('submit', typed)
}
</script>

<template>
  <form class="space-y-3 text-left" @submit.prevent="submit">
    <div>
      <FieldLabel for="device-identifier">{{ t('passkeys.device.identifierLabel') }}</FieldLabel>
      <TextInput
          id="device-identifier"
          v-model="identifier"
          autocomplete="username"
          autofocus
          data-testid="device-identifier"
          :placeholder="t('passkeys.device.identifierPlaceholder')"
      />
    </div>
    <MutedText tag="p" size="sm">{{ t('passkeys.device.identifierHint') }}</MutedText>
    <PrimaryButton
        class="w-full"
        :disabled="busy || !identifier.trim()"
        :icon="['fas', 'arrow-right']"
        type="submit"
    >
      {{ t('common.continue') }}
    </PrimaryButton>
  </form>
</template>
