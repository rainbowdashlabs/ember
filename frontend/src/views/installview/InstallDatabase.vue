/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'

const dbMode = defineModel<'bundled' | 'external'>('dbMode', {required: true})
const dbHost = defineModel<string>('dbHost', {required: true})
const dbPort = defineModel<string>('dbPort', {required: true})
const dbName = defineModel<string>('dbName', {required: true})
const dbUser = defineModel<string>('dbUser', {required: true})
const dbSchema = defineModel<string>('dbSchema', {required: true})
const dbNetwork = defineModel<string>('dbNetwork', {required: true})

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3">
    <SectionHeader>{{ t('install.database.title') }}</SectionHeader>

    <div class="space-y-1">
      <FieldLabel>{{ t('install.database.source') }}</FieldLabel>
      <SelectInput v-model="dbMode">
        <option value="bundled">{{ t('install.database.bundled') }}</option>
        <option value="external">{{ t('install.database.external') }}</option>
      </SelectInput>
    </div>

    <template v-if="dbMode === 'external'">
      <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <div class="space-y-1">
          <FieldLabel>{{ t('install.database.host') }}</FieldLabel>
          <TextInput v-model="dbHost"/>
        </div>
        <div class="space-y-1">
          <FieldLabel>{{ t('install.database.port') }}</FieldLabel>
          <TextInput v-model="dbPort"/>
        </div>
        <div class="space-y-1">
          <FieldLabel>{{ t('install.database.name') }}</FieldLabel>
          <TextInput v-model="dbName"/>
        </div>
        <div class="space-y-1">
          <FieldLabel>{{ t('install.database.user') }}</FieldLabel>
          <TextInput v-model="dbUser"/>
        </div>
        <div class="space-y-1">
          <FieldLabel>{{ t('install.database.schema') }}</FieldLabel>
          <TextInput v-model="dbSchema"/>
        </div>
        <div class="space-y-1">
          <FieldLabel>{{ t('install.database.network') }}</FieldLabel>
          <TextInput v-model="dbNetwork" :placeholder="t('install.database.networkPlaceholder')"/>
        </div>
      </div>
      <MutedText size="sm" tag="p">{{ t('install.database.networkHint') }}</MutedText>
    </template>
  </NeutralContainer>
</template>
