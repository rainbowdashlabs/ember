/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, useId} from 'vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import PasswordInput from '@/components/input/text/PasswordInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'

/**
 * One typed proof: the authenticator app's code or the password, with its own label and its own button,
 * so a keyboard reader submits it with Enter and a screen reader hears what the field asks for.
 *
 * <p>The field is emptied once it was sent, since a sent code is used up and a password is not kept on
 * the screen any longer than it takes to send it. A code is sent without the spaces an app shows in it;
 * a password exactly as typed.
 */
const props = defineProps<{
  label: string
  submitLabel: string
  /** A password is hidden as it is typed; a code is a row of digits. */
  hidden?: boolean
  disabled: boolean
}>()

const emit = defineEmits<{submit: [secret: string]}>()

const inputId = useId()
const value = ref('')

function submit() {
  const secret = props.hidden ? value.value : value.value.trim()
  if (!secret || props.disabled) return
  value.value = ''
  emit('submit', secret)
}
</script>

<template>
  <form class="space-y-2" @submit.prevent="submit">
    <FieldLabel :for="inputId">{{ label }}</FieldLabel>
    <PasswordInput v-if="hidden" :id="inputId" v-model="value" :disabled="disabled" autocomplete="current-password"/>
    <TextInput
        v-else
        :id="inputId"
        v-model="value"
        :disabled="disabled"
        autocomplete="one-time-code"
        inputmode="numeric"
        placeholder="000000"
    />
    <PrimaryButton type="submit" :disabled="disabled || !value.trim()">{{ submitLabel }}</PrimaryButton>
  </form>
</template>
