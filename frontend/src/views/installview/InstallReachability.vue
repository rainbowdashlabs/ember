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
import SelectInput from '@/components/input/select/SelectInput.vue'

const mode = defineModel<'port' | 'traefik'>('mode', {required: true})
const host = defineModel<string>('host', {required: true})
const port = defineModel<string>('port', {required: true})
const bind = defineModel<string>('bind', {required: true})
const traefikNetwork = defineModel<string>('traefikNetwork', {required: true})
const traefikEntrypoint = defineModel<string>('traefikEntrypoint', {required: true})
const traefikResolver = defineModel<string>('traefikResolver', {required: true})
</script>

<template>
  <NeutralContainer class="space-y-3">
    <SectionHeader>Erreichbarkeit</SectionHeader>
    <MutedText size="sm" tag="p">
      Der Browser ruft Oberfläche und Schnittstelle unter derselben Adresse auf. Der Frontend-Server
      reicht /api dafür selbst ans Backend weiter, ein Port auf dem Rechner genügt also.
    </MutedText>

    <div class="space-y-1">
      <FieldLabel>Wie ist Ember erreichbar?</FieldLabel>
      <SelectInput v-model="mode">
        <option value="port">Über einen Port auf dem Rechner</option>
        <option value="traefik">Über ein vorhandenes Traefik</option>
      </SelectInput>
      <MutedText size="sm" tag="p">
        {{
          mode === 'port'
              ? 'Nichts weiter davor nötig: der Frontend-Server reicht /api selbst ans Backend weiter.'
              : 'Traefik kümmert sich um Name und Zertifikat, so wie es ember-panel.de tut.'
        }}
      </MutedText>
    </div>

    <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
      <div class="space-y-1">
        <FieldLabel>{{ mode === 'traefik' ? 'Hostname' : 'Adresse der Instanz' }}</FieldLabel>
        <TextInput v-model="host" placeholder="ember.example.org"/>
      </div>
      <div v-if="mode === 'port'" class="space-y-1">
        <FieldLabel>Port</FieldLabel>
        <TextInput v-model="port" placeholder="8080"/>
      </div>
      <div v-if="mode === 'port'" class="space-y-1">
        <FieldLabel>Adresse, auf der der Port geöffnet wird</FieldLabel>
        <TextInput v-model="bind" placeholder="leer: alle Schnittstellen"/>
        <MutedText size="sm" tag="p">
          Leer öffnet den Port auf allen Schnittstellen. 127.0.0.1 hält ihn auf dem Rechner, die
          Adresse eines VPN-Interface hält ihn in diesem Netz.
        </MutedText>
      </div>
    </div>

    <div v-if="mode === 'traefik'" class="grid grid-cols-1 sm:grid-cols-3 gap-3">
      <div class="space-y-1">
        <FieldLabel>Netzwerk</FieldLabel>
        <TextInput v-model="traefikNetwork"/>
      </div>
      <div class="space-y-1">
        <FieldLabel>Entrypoint</FieldLabel>
        <TextInput v-model="traefikEntrypoint"/>
      </div>
      <div class="space-y-1">
        <FieldLabel>Zertifikatsresolver</FieldLabel>
        <TextInput v-model="traefikResolver"/>
      </div>
    </div>
  </NeutralContainer>
</template>
