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

const mode = defineModel<'port' | 'traefik'>('mode', {required: true})
const host = defineModel<string>('host', {required: true})
const port = defineModel<string>('port', {required: true})
const bind = defineModel<string>('bind', {required: true})
const traefikNetwork = defineModel<string>('traefikNetwork', {required: true})
const traefikEntrypoint = defineModel<string>('traefikEntrypoint', {required: true})
const traefikResolver = defineModel<string>('traefikResolver', {required: true})

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3">
    <SectionHeader>{{ t('install.reachability.title') }}</SectionHeader>
    <MutedText size="sm" tag="p">{{ t('install.reachability.intro') }}</MutedText>

    <div class="space-y-1">
      <FieldLabel>{{ t('install.reachability.mode') }}</FieldLabel>
      <SelectInput v-model="mode">
        <option value="port">{{ t('install.reachability.modePort') }}</option>
        <option value="traefik">{{ t('install.reachability.modeTraefik') }}</option>
      </SelectInput>
      <MutedText size="sm" tag="p">
        {{ t(mode === 'port' ? 'install.reachability.portHint' : 'install.reachability.traefikHint') }}
      </MutedText>
    </div>

    <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
      <div class="space-y-1">
        <FieldLabel>{{ t(mode === 'traefik' ? 'install.reachability.hostname' : 'install.reachability.address') }}</FieldLabel>
        <TextInput v-model="host" placeholder="ember.example.org"/>
      </div>
      <div v-if="mode === 'port'" class="space-y-1">
        <FieldLabel>{{ t('install.reachability.port') }}</FieldLabel>
        <TextInput v-model="port" placeholder="8080"/>
      </div>
      <div v-if="mode === 'port'" class="space-y-1">
        <FieldLabel>{{ t('install.reachability.bind') }}</FieldLabel>
        <TextInput v-model="bind" :placeholder="t('install.reachability.bindPlaceholder')"/>
        <MutedText size="sm" tag="p">{{ t('install.reachability.bindHint') }}</MutedText>
      </div>
    </div>

    <div v-if="mode === 'traefik'" class="grid grid-cols-1 sm:grid-cols-3 gap-3">
      <div class="space-y-1">
        <FieldLabel>{{ t('install.reachability.network') }}</FieldLabel>
        <TextInput v-model="traefikNetwork"/>
      </div>
      <div class="space-y-1">
        <FieldLabel>{{ t('install.reachability.entrypoint') }}</FieldLabel>
        <TextInput v-model="traefikEntrypoint"/>
      </div>
      <div class="space-y-1">
        <FieldLabel>{{ t('install.reachability.resolver') }}</FieldLabel>
        <TextInput v-model="traefikResolver"/>
      </div>
    </div>
  </NeutralContainer>
</template>
