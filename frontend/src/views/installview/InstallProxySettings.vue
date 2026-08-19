/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'

const tag = defineModel<string>('tag', {required: true})
const trustedProxies = defineModel<string>('trustedProxies', {required: true})
const cloudflare = defineModel<boolean>('cloudflare', {required: true})
</script>

<template>
  <NeutralContainer class="space-y-3">
    <SectionHeader>Version und Netz</SectionHeader>

    <div class="space-y-1">
      <FieldLabel>Version</FieldLabel>
      <TextInput v-model="tag" placeholder="latest"/>
    </div>

    <div class="space-y-1">
      <FieldLabel>Netzbereiche, aus denen Weiterleitungs-Header gelten</FieldLabel>
      <TextInput v-model="trustedProxies"/>
      <MutedText size="sm" tag="p">
        Vor jeder dieser Installationen steht ein Proxy. Bleibt das leer, gilt dieser Proxy für
        Ember als Besucher, mit Folgen für Zugriffsbegrenzung, Statistik und Log.
      </MutedText>
    </div>

    <div class="flex items-center gap-3">
      <ToggleInput v-model="cloudflare"/>
      <FieldLabel>Die Instanz steht hinter Cloudflare</FieldLabel>
    </div>
  </NeutralContainer>
</template>
