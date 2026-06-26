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
import SelectInput from '@/components/input/select/SelectInput.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import Modal from '@/components/feedback/Modal.vue'
import type {StationPage} from '@/api/pageManage'

const open = defineModel<boolean>({required: true})
const title = defineModel<string>('title', {required: true})
const parentId = defineModel<string>('parentId', {required: true})

const props = defineProps<{
  topLevelPages: StationPage[]
}>()

const emit = defineEmits<{
  (e: 'confirm'): void
}>()

const {t} = useI18n()
</script>

<template>
  <Modal v-model="open">
    <div class="space-y-4">
      <SectionHeader>{{ t('stationPages.createPage') }}</SectionHeader>
      <div>
        <FormLabel>{{ t('stationPages.editor.titleLabel') }}</FormLabel>
        <TextInput v-model="title" :placeholder="t('stationPages.editor.titlePlaceholder')"/>
      </div>
      <div>
        <FormLabel>{{ t('stationPages.editor.parent') }}</FormLabel>
        <SelectInput v-model="parentId">
          <option value="">{{ t('stationPages.editor.noParent') }}</option>
          <option v-for="p in props.topLevelPages" :key="p.id" :value="String(p.id)">{{ p.title }}</option>
        </SelectInput>
      </div>
      <div class="flex justify-end gap-3">
        <SecondaryButton @click="open = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="!title.trim()" @click="emit('confirm')">{{ t('common.create') }}</PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
