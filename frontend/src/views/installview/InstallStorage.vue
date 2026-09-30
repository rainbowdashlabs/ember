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

const configDir = defineModel<string>('configDir', {required: true})
const dataDir = defineModel<string>('dataDir', {required: true})
const dbVolume = defineModel<string>('dbVolume', {required: true})

defineProps<{
  /** The database keeps its own files only when it is the one brought along. */
  bundledDatabase: boolean
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3">
    <SectionHeader>{{ t('install.storage.title') }}</SectionHeader>
    <MutedText size="sm" tag="p">{{ t('install.storage.intro') }}</MutedText>

    <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
      <div class="space-y-1">
        <FieldLabel>{{ t('install.storage.config') }}</FieldLabel>
        <TextInput v-model="configDir" placeholder="./config"/>
      </div>
      <div class="space-y-1">
        <FieldLabel>{{ t('install.storage.data') }}</FieldLabel>
        <TextInput v-model="dataDir" placeholder="./data"/>
      </div>
    </div>

    <div v-if="bundledDatabase" class="space-y-1">
      <FieldLabel>{{ t('install.storage.database') }}</FieldLabel>
      <TextInput v-model="dbVolume" placeholder="ember-data"/>
      <MutedText size="sm" tag="p">{{ t('install.storage.databaseHint') }}</MutedText>
    </div>
  </NeutralContainer>
</template>
