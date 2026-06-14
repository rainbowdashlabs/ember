/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import BaseButton from './BaseButton.vue'

const props = withDefaults(defineProps<{
  /** Async action invoked on click. Reject to keep the button in idle state. */
  action: () => Promise<unknown> | unknown
  disabled?: boolean
  fullWidth?: boolean
  /** Milliseconds to display the "saved" confirmation before returning to idle. */
  confirmDuration?: number
}>(), {
  confirmDuration: 2000,
})

const emit = defineEmits<{
  saved: []
  error: [err: unknown]
}>()

const { t } = useI18n()

type State = 'idle' | 'saving' | 'saved'
const state = ref<State>('idle')
let revertTimer: ReturnType<typeof setTimeout> | null = null

async function handleClick() {
  if (state.value !== 'idle' || props.disabled) return
  state.value = 'saving'
  try {
    await props.action()
    state.value = 'saved'
    emit('saved')
    if (revertTimer) clearTimeout(revertTimer)
    revertTimer = setTimeout(() => { state.value = 'idle' }, props.confirmDuration)
  } catch (err) {
    state.value = 'idle'
    emit('error', err)
  }
}
</script>

<template>
  <BaseButton
      :disabled="disabled || state !== 'idle'"
      :full-width="fullWidth"
      :class="state === 'saved'
        ? 'bg-success text-success-text hover:brightness-110'
        : 'bg-primary text-primary-text hover:bg-primary-accent hover:text-primary-accent-text'"
      @click="handleClick"
  >
    <template v-if="state === 'saving'">
      <font-awesome-icon :icon="['fas', 'spinner']" spin class="mr-1"/>
      {{ t('common.saving') }}
    </template>
    <template v-else-if="state === 'saved'">
      <font-awesome-icon :icon="['fas', 'check']" class="mr-1"/>
      {{ t('common.saved') }}
    </template>
    <template v-else>
      <slot>{{ t('common.save') }}</slot>
    </template>
  </BaseButton>
</template>
