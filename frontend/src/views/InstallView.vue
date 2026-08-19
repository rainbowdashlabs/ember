/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import InstallReachability from './installview/InstallReachability.vue'
import InstallDatabase from './installview/InstallDatabase.vue'
import InstallProxySettings from './installview/InstallProxySettings.vue'
import InstallStorage from './installview/InstallStorage.vue'
import InstallCommand from './installview/InstallCommand.vue'
import {install} from '@/api'

/**
 * Clicking an installation together.
 *
 * What comes out is a code rather than a wall of environment variables, because the command is
 * meant to be carried to another machine, and a command nobody can retype is a command that gets
 * pasted wrong. The answers live on the server for a couple of hours; the code is the whole of what
 * has to make the journey.
 */
const mode = ref<'port' | 'traefik'>('port')
const host = ref('')
const port = ref('8080')
const bind = ref('')
const tag = ref('latest')

const traefikNetwork = ref('traefik')
const traefikEntrypoint = ref('websecure')
const traefikResolver = ref('letsencrypt')

const dbMode = ref<'bundled' | 'external'>('bundled')
const dbHost = ref('postgres')
const dbPort = ref('5432')
const dbName = ref('ember')
const dbUser = ref('ember')
const dbSchema = ref('ember_schema')
const dbNetwork = ref('')

const configDir = ref('./config')
const dataDir = ref('./data')
const dbVolume = ref('ember-data')

const trustedProxies = ref('172.16.0.0/12')
const cloudflare = ref(false)

const code = ref('')
const validForHours = ref(0)
const error = ref('')
const saving = ref(false)

const options = computed<Record<string, string>>(() => {
  const chosen: Record<string, string> = {
    EMBER_MODE: mode.value,
    EMBER_TAG: tag.value,
    EMBER_DB_MODE: dbMode.value,
    EMBER_TRUSTED_PROXIES: trustedProxies.value,
    EMBER_CLOUDFLARE: cloudflare.value ? 'true' : 'false',
    EMBER_CONFIG_DIR: configDir.value,
    EMBER_DATA_DIR: dataDir.value,
  }
  if (host.value) chosen.EMBER_HOST = host.value
  if (mode.value === 'port') {
    chosen.EMBER_PORT = port.value
    if (bind.value) chosen.EMBER_BIND = bind.value
  } else {
    chosen.EMBER_TRAEFIK_NETWORK = traefikNetwork.value
    chosen.EMBER_TRAEFIK_ENTRYPOINT = traefikEntrypoint.value
    chosen.EMBER_TRAEFIK_RESOLVER = traefikResolver.value
  }
  if (dbMode.value === 'bundled') {
    chosen.EMBER_DB_VOLUME = dbVolume.value
  }
  if (dbMode.value === 'external') {
    chosen.EMBER_DB_HOST = dbHost.value
    chosen.EMBER_DB_PORT = dbPort.value
    chosen.EMBER_DB_NAME = dbName.value
    chosen.EMBER_DB_USER = dbUser.value
    chosen.EMBER_DB_SCHEMA = dbSchema.value
    if (dbNetwork.value) chosen.EMBER_DB_NETWORK = dbNetwork.value
  }
  return chosen
})

/** The password is never part of a preset. It is asked for on the machine that will hold it. */
const ready = computed(() => mode.value !== 'traefik' || host.value.trim().length > 0)

async function generate() {
  saving.value = true
  error.value = ''
  try {
    const preset = await install.createPreset(options.value)
    code.value = preset.code
    validForHours.value = preset.validForHours
  } catch {
    error.value = 'Der Code konnte nicht erzeugt werden. Versuche es noch einmal.'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <ViewContent subtitle="Klicke die Installation zusammen und nimm den Code mit auf den Server."
               title="Ember installieren">
    <div class="mx-auto w-full max-w-3xl space-y-4">
      <InstallReachability
          v-model:host="host"
          v-model:mode="mode"
          v-model:bind="bind"
          v-model:port="port"
          v-model:traefik-entrypoint="traefikEntrypoint"
          v-model:traefik-network="traefikNetwork"
          v-model:traefik-resolver="traefikResolver"/>

      <InstallDatabase
          v-model:db-host="dbHost"
          v-model:db-mode="dbMode"
          v-model:db-name="dbName"
          v-model:db-network="dbNetwork"
          v-model:db-port="dbPort"
          v-model:db-schema="dbSchema"
          v-model:db-user="dbUser"/>

      <InstallStorage
          v-model:config-dir="configDir"
          v-model:data-dir="dataDir"
          v-model:db-volume="dbVolume"
          :bundled-database="dbMode === 'bundled'"/>

      <InstallProxySettings
          v-model:cloudflare="cloudflare"
          v-model:tag="tag"
          v-model:trusted-proxies="trustedProxies"/>

      <NeutralContainer class="space-y-3">
        <SectionHeader>Befehl erzeugen</SectionHeader>
        <MutedText size="sm" tag="p">
          Das Passwort der Datenbank ist nie Teil eines Codes. Danach fragt der Installer auf dem
          Rechner, auf dem es auch bleibt.
        </MutedText>
        <Alert v-if="error" variant="error">{{ error }}</Alert>
        <PrimaryButton :disabled="saving || !ready" :icon="['fas', 'code']" @click="generate">
          {{ saving ? 'Einen Moment...' : 'Code erzeugen' }}
        </PrimaryButton>
        <MutedText v-if="!ready" size="sm" tag="p">
          Für Traefik wird ein Hostname gebraucht.
        </MutedText>
      </NeutralContainer>

      <InstallCommand v-if="code" :code="code" :options="options" :valid-for-hours="validForHours"/>
    </div>
  </ViewContent>
</template>
