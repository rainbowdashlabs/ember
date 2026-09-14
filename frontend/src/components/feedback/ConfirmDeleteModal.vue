/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'

const {t} = useI18n()

const modelValue = defineModel<boolean>({required: true})

defineProps<{
  message: string
}>()

const emit = defineEmits<{
  confirm: []
}>()
</script>

<template>
  <Modal v-model="modelValue">
    <div class="space-y-4">
      <p>{{ message }}</p>
      <ButtonRow pair align="end">
        <SecondaryButton data-cancel @click="modelValue = false">{{ t('common.cancel') }}</SecondaryButton>
        <ErrorButton data-confirm @click="emit('confirm')">{{ t('common.delete') }}</ErrorButton>
      </ButtonRow>
    </div>
  </Modal>
</template>
