/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import MutedText from '@/components/typography/MutedText.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import type {PasskeysStatus} from '@/api/passkeys'

/**
 * The two switches D3 names: asking for the passkey after the password as well, and switching
 * password sign-in off. The second appears only where the server would accept it, so the screen
 * never shows a control that answers with a refusal.
 */
const {t} = useI18n()

const props = defineProps<{status: PasskeysStatus}>()

const emit = defineEmits<{
  (e: 'togglePasswordLogin', enabled: boolean): void
  (e: 'toggleAskWithPassword', enabled: boolean): void
}>()

const passwordSwitchVisible = computed(() =>
  props.status.hasPassword && (props.status.mayDisablePasswordLogin || !props.status.passwordLoginEnabled))
</script>

<template>
  <div class="space-y-3 border-t border-(--border) pt-4">
    <label class="flex items-start justify-between gap-3">
      <span class="text-sm">
        {{ t('passkeys.section.askWithPassword') }}
        <MutedText tag="span" class="block" size="sm">{{ t('passkeys.section.askWithPasswordHint') }}</MutedText>
      </span>
      <ToggleInput :model-value="status.askWithPassword"
                   @update:model-value="(v: boolean) => emit('toggleAskWithPassword', v)"/>
    </label>
    <label v-if="passwordSwitchVisible" class="flex items-start justify-between gap-3">
      <span class="text-sm">
        {{ t('passkeys.section.passwordLogin') }}
        <MutedText tag="span" class="block" size="sm">{{ t('passkeys.section.passwordLoginHint') }}</MutedText>
      </span>
      <ToggleInput :model-value="status.passwordLoginEnabled"
                   @update:model-value="(v: boolean) => emit('togglePasswordLogin', v)"/>
    </label>
  </div>
</template>
