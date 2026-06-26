/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import type { StationMember } from '@/api/types'

const modelValue = defineModel<boolean>({required: true})
const memberId = defineModel<string>('memberId', {required: true})

defineProps<{
  members: StationMember[]
}>()

const emit = defineEmits<{
  submit: []
}>()

const { t } = useI18n()
</script>

<template>
  <Modal v-model="modelValue">
    <div class="space-y-3">
      <SectionHeader>{{ t('inventory.detail.assign') }}</SectionHeader>
      <SelectInput v-model="memberId">
        <option value="" disabled>{{ t('inventory.detail.selectMember') }}</option>
        <option v-for="m in members" :key="m.id" :value="String(m.id)">
          {{ m.name || m.email || `#${m.id}` }}
        </option>
      </SelectInput>
      <div class="flex justify-end gap-2">
        <SecondaryButton @click="modelValue = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="!memberId" @click="emit('submit')">{{ t('inventory.detail.assign') }}</PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
