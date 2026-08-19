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

const dbMode = defineModel<'bundled' | 'external'>('dbMode', {required: true})
const dbHost = defineModel<string>('dbHost', {required: true})
const dbPort = defineModel<string>('dbPort', {required: true})
const dbName = defineModel<string>('dbName', {required: true})
const dbUser = defineModel<string>('dbUser', {required: true})
const dbSchema = defineModel<string>('dbSchema', {required: true})
const dbNetwork = defineModel<string>('dbNetwork', {required: true})
</script>

<template>
  <NeutralContainer class="space-y-3">
    <SectionHeader>Datenbank</SectionHeader>

    <div class="space-y-1">
      <FieldLabel>Woher kommt PostgreSQL?</FieldLabel>
      <SelectInput v-model="dbMode">
        <option value="bundled">Mitgeliefert, wird als Container gestartet</option>
        <option value="external">Vorhanden, wird nur eingebunden</option>
      </SelectInput>
    </div>

    <template v-if="dbMode === 'external'">
      <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <div class="space-y-1">
          <FieldLabel>Host</FieldLabel>
          <TextInput v-model="dbHost"/>
        </div>
        <div class="space-y-1">
          <FieldLabel>Port</FieldLabel>
          <TextInput v-model="dbPort"/>
        </div>
        <div class="space-y-1">
          <FieldLabel>Datenbank</FieldLabel>
          <TextInput v-model="dbName"/>
        </div>
        <div class="space-y-1">
          <FieldLabel>Benutzer</FieldLabel>
          <TextInput v-model="dbUser"/>
        </div>
        <div class="space-y-1">
          <FieldLabel>Schema</FieldLabel>
          <TextInput v-model="dbSchema"/>
        </div>
        <div class="space-y-1">
          <FieldLabel>Docker-Netzwerk zur Datenbank</FieldLabel>
          <TextInput v-model="dbNetwork" placeholder="leer, wenn keins nötig"/>
        </div>
      </div>
      <MutedText size="sm" tag="p">
        Läuft die Datenbank selbst in Docker, erreicht Ember sie nur über ein gemeinsames Netzwerk.
        Liegt sie auf dem Host oder auf einem anderen Rechner, bleibt das Feld leer.
      </MutedText>
    </template>
  </NeutralContainer>
</template>
