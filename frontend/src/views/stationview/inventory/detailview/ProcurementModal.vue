/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import Alert from '@/components/feedback/Alert.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import type { InventorySize, StationMember } from '@/api/types'

const modelValue = defineModel<boolean>({required: true})
const memberId = defineModel<string>('memberId', {required: true})
const sizeId = defineModel<string>('sizeId', {required: true})
const notes = defineModel<string>('notes', {required: true})

defineProps<{
  created: boolean
  hasSizes: boolean
  sizes: InventorySize[] | undefined
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
      <SectionHeader>{{ t('inventory.detail.createProcurement') }}</SectionHeader>
      <Alert v-if="created" variant="success">{{ t('inventory.check.procurementCreated') }}</Alert>
      <template v-if="!created">
        <SelectInput v-model="memberId">
          <option value="" disabled>{{ t('inventory.detail.selectMember') }}</option>
          <option v-for="m in members" :key="m.id" :value="String(m.id)">
            {{ m.name || m.email || `#${m.id}` }}
          </option>
        </SelectInput>
        <SelectInput v-if="hasSizes" v-model="sizeId">
          <option value="">{{ t('inventory.detail.anySize') }}</option>
          <option v-for="s in sizes" :key="s.id" :value="String(s.id)">{{ s.label }}</option>
        </SelectInput>
        <TextAreaInput v-model="notes" :placeholder="t('inventory.detail.procurementNotes')" :rows="2" />
      </template>
      <div class="flex justify-end gap-2">
        <SecondaryButton @click="modelValue = false">{{ t('common.close') }}</SecondaryButton>
        <PrimaryButton v-if="!created" :disabled="!memberId" @click="emit('submit')">{{ t('common.save') }}</PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
