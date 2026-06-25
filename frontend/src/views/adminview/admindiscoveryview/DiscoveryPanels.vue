/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {discovery} from '@/api'
import type {
  BlocklistKind,
  DiscoveryBlocklistEntry,
  DiscoveryIdentity,
  DiscoveryInfoProbe,
  DiscoveryPeer,
  DiscoveryPeerSource,
  DiscoverySettings,
} from '@/api/discovery'
import DiscoveryIdentityCard from './DiscoveryIdentityCard.vue'
import DiscoverySettingsCard from './DiscoverySettingsCard.vue'
import AddPeerCard from './AddPeerCard.vue'
import PeerListCard from './PeerListCard.vue'
import BlocklistCard from './BlocklistCard.vue'

const {t} = useI18n()

const flash = ref('')

const identity = ref<DiscoveryIdentity | null>(null)
const settings = ref<DiscoverySettings | null>(null)
const peers = ref<DiscoveryPeer[]>([])
const blocklist = ref<DiscoveryBlocklistEntry[]>([])

const draftEnabled = ref(true)
const draftDepth = ref(2)
const draftInterval = ref(60)

const probeBaseUrl = ref('')
const probeExpectedKey = ref('')
const probeResult = ref<DiscoveryInfoProbe | null>(null)
const probing = ref(false)
const probeError = ref('')

const blocklistValue = ref('')
const blocklistKind = ref<BlocklistKind>('BASE_URL')
const blocklistNote = ref('')

const peerActionInFlight = ref<string | null>(null)

const sourceLabel: Record<DiscoveryPeerSource, string> = {
  BOOTSTRAP: t('adminDiscovery.sourceBootstrap'),
  GOSSIP: t('adminDiscovery.sourceGossip'),
  MANUAL: t('adminDiscovery.sourceManual'),
}

const {loading, error} = useAsyncLoader(async () => {
  const [id, set, pl, bl] = await Promise.all([
    discovery.getDiscoveryIdentity(),
    discovery.getDiscoverySettings(),
    discovery.listDiscoveryPeers(),
    discovery.listDiscoveryBlocklist(),
  ])
  identity.value = id
  settings.value = set
  peers.value = pl
  blocklist.value = bl
  draftEnabled.value = set.enabled
  draftDepth.value = set.maxDepth
  draftInterval.value = set.pingIntervalMinutes
})

async function saveSettings() {
  if (!settings.value) return
  flash.value = ''
  try {
    settings.value = await discovery.updateDiscoverySettings({
      enabled: draftEnabled.value,
      maxDepth: draftDepth.value,
      pingIntervalMinutes: draftInterval.value,
    })
  } catch (e) {
    error.value = t('common.error')
    throw e
  }
}

async function probe() {
  probing.value = true
  probeError.value = ''
  probeResult.value = null
  try {
    probeResult.value = await discovery.probeDiscoveryPeer(probeBaseUrl.value.trim())
  } catch {
    probeError.value = t('adminDiscovery.probeFailed')
  } finally {
    probing.value = false
  }
}

async function addPeer() {
  if (!probeBaseUrl.value.trim()) return
  flash.value = ''
  try {
    await discovery.addDiscoveryPeer(probeBaseUrl.value.trim(), probeExpectedKey.value.trim() || undefined)
    probeBaseUrl.value = ''
    probeExpectedKey.value = ''
    probeResult.value = null
    flash.value = t('adminDiscovery.added')
    peers.value = await discovery.listDiscoveryPeers()
  } catch {
    error.value = t('common.error')
  }
}

async function runPeerAction(p: DiscoveryPeer, action: () => Promise<unknown>) {
  peerActionInFlight.value = p.publicKey
  try {
    await action()
    peers.value = await discovery.listDiscoveryPeers()
  } catch {
    error.value = t('common.error')
  } finally {
    peerActionInFlight.value = null
  }
}

async function deletePeer(p: DiscoveryPeer) {
  if (!confirm(t('adminDiscovery.confirmDeletePeer'))) return
  await runPeerAction(p, () => discovery.deleteDiscoveryPeer(p.publicKey))
}

async function discoverNow() {
  flash.value = ''
  try {
    const result = await discovery.discoverNow()
    flash.value = t('adminDiscovery.discoverNowResult', {
      pings: result.pingsDispatched,
      stations: result.stationsFetched,
    })
    peers.value = await discovery.listDiscoveryPeers()
  } catch {
    error.value = t('common.error')
  }
}

async function seedFederation() {
  flash.value = ''
  try {
    const count = await discovery.seedFromFederation()
    flash.value = t('adminDiscovery.seedFederationResult', {count})
    peers.value = await discovery.listDiscoveryPeers()
  } catch {
    error.value = t('common.error')
  }
}

async function addToBlocklist() {
  if (!blocklistValue.value.trim()) return
  try {
    await discovery.addToBlocklist(blocklistValue.value.trim(), blocklistKind.value, blocklistNote.value.trim() || undefined)
    blocklistValue.value = ''
    blocklistNote.value = ''
    blocklist.value = await discovery.listDiscoveryBlocklist()
  } catch {
    error.value = t('common.error')
  }
}

async function removeFromBlocklist(entry: DiscoveryBlocklistEntry) {
  try {
    await discovery.removeFromBlocklist(entry.value)
    blocklist.value = await discovery.listDiscoveryBlocklist()
  } catch {
    error.value = t('common.error')
  }
}

function formatTimestamp(ts: string | null): string {
  if (!ts) return '-'
  return new Date(ts).toLocaleString('de-DE', {
    day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  })
}

const sortedPeers = computed(() =>
    [...peers.value].sort((a, b) => (a.lastSeenAt > b.lastSeenAt ? -1 : 1)),
)

</script>

<template>
  <Spinner v-if="loading" size="lg"/>
  <Alert v-if="error" variant="error">{{ error }}</Alert>
  <Alert v-if="flash" variant="success">{{ flash }}</Alert>

  <template v-if="!loading && identity">
    <DiscoveryIdentityCard :identity="identity"/>

    <DiscoverySettingsCard
        v-if="settings"
        :settings="settings"
        v-model:model-enabled="draftEnabled"
        v-model:model-depth="draftDepth"
        v-model:model-interval="draftInterval"
        :save="saveSettings"
        @discover-now="discoverNow"
        @seed-federation="seedFederation"
    />

    <AddPeerCard
        v-model:base-url="probeBaseUrl"
        v-model:expected-key="probeExpectedKey"
        :probing="probing"
        :probe-error="probeError"
        :probe-result="probeResult"
        @probe="probe"
        @add="addPeer"
    />

    <PeerListCard
        :peers="sortedPeers"
        :source-label="sourceLabel"
        :in-flight-key="peerActionInFlight"
        :format-timestamp="formatTimestamp"
        @upvote="(p) => runPeerAction(p, () => discovery.upvoteDiscoveryPeer(p.publicKey))"
        @downvote="(p) => runPeerAction(p, () => discovery.downvoteDiscoveryPeer(p.publicKey))"
        @block="(p) => runPeerAction(p, () => discovery.blockDiscoveryPeer(p.publicKey))"
        @unblock="(p) => runPeerAction(p, () => discovery.unblockDiscoveryPeer(p.publicKey))"
        @ping="(p) => runPeerAction(p, () => discovery.pingDiscoveryPeerNow(p.publicKey))"
        @remove="deletePeer"
    />

    <BlocklistCard
        :blocklist="blocklist"
        v-model:value="blocklistValue"
        v-model:kind="blocklistKind"
        v-model:note="blocklistNote"
        @add="addToBlocklist"
        @remove="removeFromBlocklist"
    />
  </template>
</template>
