/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import ColorInput from '@/components/input/ColorInput.vue'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {useModelProxy} from '@/composables/useModelProxy'

const {t} = useI18n()

const props = defineProps<{
  modelValue: boolean
  isEdit: boolean
  name: string
  color: string
  saving: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'update:name', v: string): void
  (e: 'update:color', v: string): void
  (e: 'save'): void
}>()

const open = useModelProxy(() => props.modelValue, emit, 'modelValue')
const nameModel = useModelProxy(() => props.name, emit, 'name')
const colorModel = useModelProxy(() => props.color, emit, 'color')
</script>

<template>
  <Modal v-model="open">
    <div class="space-y-4">
      <SubHeader>{{ isEdit ? t('memberGroups.editTitle') : t('memberGroups.createTitle') }}</SubHeader>
      <div class="space-y-1">
        <FieldLabel>{{ t('memberGroups.name') }}</FieldLabel>
        <TextInput v-model="nameModel" :placeholder="t('memberGroups.namePlaceholder')"/>
      </div>
      <div class="space-y-1">
        <FieldLabel>{{ t('memberGroups.color') }}</FieldLabel>
        <div class="flex items-center gap-2">
          <ColorInput v-model="colorModel"/>
          <SecondaryButton v-if="colorModel" compact @click="colorModel = ''">
            <font-awesome-icon :icon="['fas', 'xmark']"/>
          </SecondaryButton>
          <MutedText size="sm">{{ t('memberGroups.colorHint') }}</MutedText>
        </div>
      </div>
      <div class="flex justify-end gap-3">
        <SecondaryButton @click="open = false">{{ t('memberGroups.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="saving || !nameModel" @click="emit('save')">
          {{ saving ? t('common.loading') : t('memberGroups.save') }}
        </PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
