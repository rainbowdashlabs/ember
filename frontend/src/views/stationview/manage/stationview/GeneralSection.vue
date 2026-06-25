/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SaveButton from '@/components/button/SaveButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import SearchSelectInput from '@/components/input/select/SearchSelectInput.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'

const name = defineModel<string>('name', {required: true})
const timezone = defineModel<string>('timezone', {required: true})
const locale = defineModel<string>('locale', {required: true})

const props = defineProps<{
  timezoneOptions: { value: string; label: string }[]
  localeOptions: { value: string; label: string }[]
  saveName: () => Promise<void>
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SectionHeader>{{ t('stationManage.nameTitle') }}</SectionHeader>
    <div class="space-y-1">
      <FieldLabel>{{ t('stationManage.name') }}</FieldLabel>
      <TextInput v-model="name" :placeholder="t('stationManage.namePlaceholder')"/>
    </div>
    <div class="space-y-1">
      <FieldLabel>{{ t('stationManage.timezone') }}</FieldLabel>
      <SearchSelectInput v-model="timezone" :options="props.timezoneOptions" :placeholder="t('stationManage.timezone')"/>
    </div>
    <div class="space-y-1">
      <FieldLabel>{{ t('stationManage.locale') }}</FieldLabel>
      <SelectInput v-model="locale">
        <option v-for="opt in props.localeOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
      </SelectInput>
    </div>
    <SaveButton :disabled="!name" :action="props.saveName"/>
  </NeutralContainer>
</template>
