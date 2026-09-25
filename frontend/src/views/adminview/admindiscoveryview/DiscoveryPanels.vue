/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import Spinner from '@/components/feedback/Spinner.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import Alert from '@/components/feedback/Alert.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useFlashMessage} from '@/composables/useFlashMessage'
import {useConfirmAction} from '@/composables/useConfirmAction'
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
import {apiErrorMessage} from '@/util/apiError'
import {describeFailure} from '@/util/failure'
import DiscoveryIdentityCard from './DiscoveryIdentityCard.vue'
import DiscoverySettingsCard from './DiscoverySettingsCard.vue'
import AddPeerCard from './AddPeerCard.vue'
import PeerListCard from './PeerListCard.vue'
import BlocklistCard from './BlocklistCard.vue'

const {t} = useI18n()

const {message: flash, flash: showFlash} = useFlashMessage()

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

const blocklistValue = ref('')
const blocklistKind = ref<BlocklistKind>('BASE_URL')
const blocklistNote = ref('')

const peerActionInFlight = ref<string | null>(null)

const sourceLabel: Record<DiscoveryPeerSource, string> = {
  BOOTSTRAP: t('adminDiscovery.sourceBootstrap'),
  GOSSIP: t('adminDiscovery.sourceGossip'),
  MANUAL: t('adminDiscovery.sourceManual'),
}

const {loading, failure} = useAsyncLoader(async () => {
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

/** Records what went wrong into the one alert this page carries. */
function record(e: unknown) {
  failure.value = describeFailure(e, t)
}

async function saveSettings() {
  if (!settings.value) return
  try {
    settings.value = await discovery.updateDiscoverySettings({
      enabled: draftEnabled.value,
      maxDepth: draftDepth.value,
      pingIntervalMinutes: draftInterval.value,
    })
  } catch (e) {
    record(e)
    throw e
  }
}

/**
 * Asks a peer who it is, and keeps its answer.
 *
 * <p>The whole point of the button is to find out why an address does not work, so the reason the
 * peer or the network gave is the result. It used to be replaced by "the probe failed", which is
 * the one thing the reader already knew.
 */
const {running: probing, failure: probeFailure, run: probe} = useAsyncAction(async () => {
  probeResult.value = null
  probeResult.value = await discovery.probeDiscoveryPeer(probeBaseUrl.value.trim())
}, {formatError: (e) => apiErrorMessage(e) ?? t('adminDiscovery.probeFailed')})

async function addPeer() {
  if (!probeBaseUrl.value.trim()) return
  try {
    await discovery.addDiscoveryPeer(probeBaseUrl.value.trim(), probeExpectedKey.value.trim() || undefined)
    probeBaseUrl.value = ''
    probeExpectedKey.value = ''
    probeResult.value = null
    showFlash(t('adminDiscovery.added'))
    peers.value = await discovery.listDiscoveryPeers()
  } catch (e) {
    record(e)
  }
}

async function runPeerAction(p: DiscoveryPeer, action: () => Promise<unknown>) {
  peerActionInFlight.value = p.publicKey
  try {
    await action()
    peers.value = await discovery.listDiscoveryPeers()
  } catch (e) {
    record(e)
  } finally {
    peerActionInFlight.value = null
  }
}

const {show: showDeletePeer, request: requestDeletePeer, confirm: confirmDeletePeer} = useConfirmAction<DiscoveryPeer>({
  onConfirm: (p) => runPeerAction(p, () => discovery.deleteDiscoveryPeer(p.publicKey)),
  failure,
})

async function discoverNow() {
  try {
    const result = await discovery.discoverNow()
    showFlash(t('adminDiscovery.discoverNowResult', {
      pings: result.pingsDispatched,
      stations: result.stationsFetched,
    }))
    peers.value = await discovery.listDiscoveryPeers()
  } catch (e) {
    record(e)
  }
}

async function seedFederation() {
  try {
    const count = await discovery.seedFromFederation()
    showFlash(t('adminDiscovery.seedFederationResult', {count}))
    peers.value = await discovery.listDiscoveryPeers()
  } catch (e) {
    record(e)
  }
}

async function addToBlocklist() {
  if (!blocklistValue.value.trim()) return
  try {
    await discovery.addToBlocklist(blocklistValue.value.trim(), blocklistKind.value, blocklistNote.value.trim() || undefined)
    blocklistValue.value = ''
    blocklistNote.value = ''
    blocklist.value = await discovery.listDiscoveryBlocklist()
  } catch (e) {
    record(e)
  }
}

async function removeFromBlocklist(entry: DiscoveryBlocklistEntry) {
  try {
    await discovery.removeFromBlocklist(entry.value)
    blocklist.value = await discovery.listDiscoveryBlocklist()
  } catch (e) {
    record(e)
  }
}

const sortedPeers = computed(() =>
    [...peers.value].sort((a, b) => (a.lastSeenAt > b.lastSeenAt ? -1 : 1)),
)

</script>

<template>
  <Spinner v-if="loading" size="lg"/>
  <FailureAlert :failure="failure"/>
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
        :probe-failure="probeFailure"
        :probe-result="probeResult"
        @probe="probe"
        @add="addPeer"
    />

    <PeerListCard
        :peers="sortedPeers"
        :source-label="sourceLabel"
        :in-flight-key="peerActionInFlight"
        @upvote="(p) => runPeerAction(p, () => discovery.upvoteDiscoveryPeer(p.publicKey))"
        @downvote="(p) => runPeerAction(p, () => discovery.downvoteDiscoveryPeer(p.publicKey))"
        @block="(p) => runPeerAction(p, () => discovery.blockDiscoveryPeer(p.publicKey))"
        @unblock="(p) => runPeerAction(p, () => discovery.unblockDiscoveryPeer(p.publicKey))"
        @ping="(p) => runPeerAction(p, () => discovery.pingDiscoveryPeerNow(p.publicKey))"
        @remove="requestDeletePeer"
    />

    <ConfirmDeleteModal
        v-model="showDeletePeer"
        :message="t('adminDiscovery.confirmDeletePeer')"
        @confirm="confirmDeletePeer"
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
