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
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import Modal from '@/components/feedback/Modal.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {useModelProxy} from '@/composables/useModelProxy'

const {t} = useI18n()

const props = defineProps<{
  modelValue: boolean
  isEdit: boolean
  name: string
  color: string
  visible: boolean
  saving: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'update:name', v: string): void
  (e: 'update:color', v: string): void
  (e: 'update:visible', v: boolean): void
  (e: 'save'): void
}>()

const open = useModelProxy(() => props.modelValue, emit, 'modelValue')
const nameModel = useModelProxy(() => props.name, emit, 'name')
const colorModel = useModelProxy(() => props.color, emit, 'color')
const visibleModel = useModelProxy(() => props.visible, emit, 'visible')
</script>

<template>
  <Modal v-model="open">
    <div class="space-y-4">
      <SectionHeader>{{ isEdit ? t('userTags.editTitle') : t('userTags.createTitle') }}</SectionHeader>
      <div class="space-y-1">
        <FieldLabel>{{ t('userTags.name') }}</FieldLabel>
        <TextInput v-model="nameModel" :placeholder="t('userTags.namePlaceholder')"/>
      </div>
      <div class="space-y-1">
        <FieldLabel>{{ t('userTags.color') }}</FieldLabel>
        <div class="flex items-center gap-2">
          <ColorInput v-model="colorModel"/>
          <SecondaryButton v-if="colorModel" compact @click="colorModel = ''">
            <font-awesome-icon :icon="['fas', 'xmark']"/>
          </SecondaryButton>
        </div>
      </div>
      <div class="flex items-center gap-2">
        <FieldLabel>{{ t('userTags.visible') }}</FieldLabel>
        <ToggleInput v-model="visibleModel"/>
      </div>
      <MutedText size="sm">{{ t('userTags.visibleHint') }}</MutedText>
      <div class="flex justify-end gap-3">
        <SecondaryButton @click="open = false">{{ t('userTags.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="saving || !nameModel" @click="emit('save')">
          {{ saving ? t('common.loading') : t('userTags.save') }}
        </PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
