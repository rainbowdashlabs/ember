/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, useId, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'

/**
 * The last question before the signing keys that no longer open are given up.
 *
 * <p>It says what happens and what stays, and the button stays disabled until the administrator ticks
 * that the old key file cannot be restored, so the step is never one click away. The box is cleared each
 * time the dialog opens.
 */
const open = defineModel<boolean>({required: true})

defineProps<{
  /** How many keys are given up. */
  count: number
  /** Set while the keys are being given up, so nothing is pressed twice. */
  busy: boolean
}>()

defineEmits<{confirm: []}>()

const {t} = useI18n()
const checked = ref(false)
const boxId = useId()

watch(open, (isOpen) => {
  if (isOpen) checked.value = false
})
</script>

<template>
  <Modal v-model="open">
    <div class="space-y-4">
      <SubHeader>{{ t('adminSecurity.signingKeys.confirmTitle') }}</SubHeader>
      <p>{{ t('adminSecurity.signingKeys.confirmText', {count}) }}</p>
      <p class="text-sm">{{ t('adminSecurity.signingKeys.confirmKeeps') }}</p>
      <FieldLabel inline :for="boxId" class="cursor-pointer">
        <CheckboxInput :id="boxId" v-model="checked" data-testid="signing-keys-confirm-check"/>
        {{ t('adminSecurity.signingKeys.confirmCheck') }}
      </FieldLabel>
      <ButtonRow pair align="end">
        <SecondaryButton :disabled="busy" @click="open = false">{{ t('common.cancel') }}</SecondaryButton>
        <ErrorButton
            :icon="['fas', 'key']"
            :disabled="!checked || busy"
            data-testid="signing-keys-confirm"
            @click="$emit('confirm')"
        >
          {{ t('adminSecurity.signingKeys.confirmButton') }}
        </ErrorButton>
      </ButtonRow>
    </div>
  </Modal>
</template>
