/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import ColorInput from '@/components/input/ColorInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {TagVisibility} from '@/api/generated/schema'

const {t} = useI18n()

const props = defineProps<{
  isEdit: boolean
  saving: boolean
  /** Only a reader allowed to view members may make a tag private, since nobody else would see it. */
  canMakePrivate: boolean
}>()

const emit = defineEmits<{
  save: []
}>()

const open = defineModel<boolean>({required: true})
const nameModel = defineModel<string>('name', {required: true})
const colorModel = defineModel<string>('color', {required: true})
const visibilityModel = defineModel<TagVisibility>('visibility', {required: true})

const offeredVisibilities = computed(() => Object.values(TagVisibility)
    .filter(visibility => visibility !== TagVisibility.PRIVATE || props.canMakePrivate
        || visibilityModel.value === TagVisibility.PRIVATE))
</script>

<template>
  <Modal v-model="open">
    <div class="space-y-4">
      <SubHeader>{{ isEdit ? t('userTags.editTitle') : t('userTags.createTitle') }}</SubHeader>
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
      <div class="space-y-1">
        <FieldLabel>{{ t('userTags.visibility') }}</FieldLabel>
        <SelectInput v-model="visibilityModel" class="w-full">
          <option v-for="visibility in offeredVisibilities" :key="visibility" :value="visibility">
            {{ t(`userTags.visibilities.${visibility}.label`) }}
          </option>
        </SelectInput>
        <MutedText size="sm">{{ t(`userTags.visibilities.${visibilityModel}.hint`) }}</MutedText>
      </div>
      <ButtonRow pair align="end">
        <SecondaryButton @click="open = false">{{ t('userTags.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="saving || !nameModel" @click="emit('save')">
          {{ saving ? t('common.loading') : t('userTags.save') }}
        </PrimaryButton>
      </ButtonRow>
    </div>
  </Modal>
</template>
