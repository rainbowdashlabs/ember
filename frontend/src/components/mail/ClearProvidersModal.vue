/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'

/**
 * The one way to empty the provider list, which is why it asks first: a save can no longer do it
 * by accident, so this is the deliberate act it was separated out to be.
 */
const show = defineModel<boolean>({required: true})

defineProps<{
  clearing: boolean
}>()

const emit = defineEmits<{
  confirm: []
}>()

const {t} = useI18n()
</script>

<template>
  <Modal v-model="show">
    <div class="space-y-4">
      <p>{{ t('adminSettings.mailing.clearConfirm') }}</p>
      <div class="flex justify-end gap-3">
        <SecondaryButton :disabled="clearing" @click="show = false">{{ t('common.cancel') }}</SecondaryButton>
        <ErrorButton :icon="['fas', 'trash']" :disabled="clearing" @click="emit('confirm')">
          {{ t('adminSettings.mailing.clear') }}
        </ErrorButton>
      </div>
    </div>
  </Modal>
</template>
