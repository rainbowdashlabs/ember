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
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'

const tag = defineModel<string>('tag', {required: true})
const trustedProxies = defineModel<string>('trustedProxies', {required: true})
const cloudflare = defineModel<boolean>('cloudflare', {required: true})
const autoUpdate = defineModel<boolean>('autoUpdate', {required: true})

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3">
    <SectionHeader>{{ t('install.proxy.title') }}</SectionHeader>

    <div class="space-y-1">
      <FieldLabel>{{ t('install.proxy.version') }}</FieldLabel>
      <TextInput v-model="tag" placeholder="latest"/>
    </div>

    <div class="space-y-1">
      <FieldLabel>{{ t('install.proxy.trustedProxies') }}</FieldLabel>
      <TextInput v-model="trustedProxies"/>
      <MutedText size="sm" tag="p">{{ t('install.proxy.trustedProxiesHint') }}</MutedText>
    </div>

    <div class="flex items-center gap-3">
      <ToggleInput v-model="cloudflare"/>
      <FieldLabel>{{ t('install.proxy.cloudflare') }}</FieldLabel>
    </div>

    <div class="space-y-1">
      <div class="flex items-center gap-3">
        <ToggleInput v-model="autoUpdate"/>
        <FieldLabel>{{ t('install.proxy.autoUpdate') }}</FieldLabel>
      </div>
      <MutedText size="sm" tag="p">{{ t('install.proxy.autoUpdateHint') }}</MutedText>
    </div>
  </NeutralContainer>
</template>
