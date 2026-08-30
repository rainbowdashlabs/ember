/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'

/**
 * The question asked before somebody is signed off an appointment.
 *
 * <p>A place given up is gone: whoever wants it back joins the queue again, and where the list is
 * already full that is the end of it.
 */
const open = defineModel<boolean>({required: true})

defineProps<{
  busy: boolean
}>()

const emit = defineEmits<{
  confirm: []
}>()

const {t} = useI18n()
</script>

<template>
  <Modal v-model="open">
    <div class="space-y-4">
      <SubHeader>{{ t('eventsUpcoming.signOffConfirmTitle') }}</SubHeader>
      <p class="text-sm">{{ t('eventsUpcoming.signOffConfirmBody') }}</p>
      <div class="flex justify-end gap-2">
        <SecondaryButton @click="open = false">{{ t('common.cancel') }}</SecondaryButton>
        <ErrorButton :disabled="busy" data-testid="confirm-sign-off" @click="emit('confirm')">
          {{ t('eventsUpcoming.decline') }}
        </ErrorButton>
      </div>
    </div>
  </Modal>
</template>
