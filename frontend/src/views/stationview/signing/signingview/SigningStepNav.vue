/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'

/**
 * "Zurück" and "Weiter" under a screen of the signing flow. Going back always works and keeps what was
 * ticked and typed; going on waits for what the screen asks.
 */
withDefaults(defineProps<{
  /** Whether the screen still waits for something before it can be left forwards. */
  blocked?: boolean
}>(), {
  blocked: false,
})

defineEmits<{next: []; back: []}>()

const {t} = useI18n()
</script>

<template>
  <ButtonRow pair>
    <SecondaryButton type="button" data-testid="signing-back" @click="$emit('back')">{{ t('signing.flow.back') }}</SecondaryButton>
    <PrimaryButton type="button" :disabled="blocked" data-testid="signing-next" @click="$emit('next')">
      {{ t('signing.flow.next') }}
    </PrimaryButton>
  </ButtonRow>
</template>
