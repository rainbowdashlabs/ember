/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import Modal from '@/components/feedback/Modal.vue'
import type {UserTag} from '@/api/types'
import {useModelProxy} from '@/composables/useModelProxy'

const {t} = useI18n()

const props = defineProps<{
  modelValue: boolean
  target: UserTag | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'confirm'): void
}>()

const open = useModelProxy(() => props.modelValue, emit, 'modelValue')
</script>

<template>
  <Modal v-model="open">
    <div class="space-y-4">
      <p>{{ t('userTags.deleteConfirmDetail', {name: target?.name}) }}</p>
      <div class="flex justify-end gap-3">
        <SecondaryButton @click="open = false">{{ t('userTags.cancel') }}</SecondaryButton>
        <ErrorButton @click="emit('confirm')">{{ t('userTags.delete') }}</ErrorButton>
      </div>
    </div>
  </Modal>
</template>
