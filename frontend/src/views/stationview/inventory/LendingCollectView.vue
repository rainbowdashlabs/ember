/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Alert from '@/components/feedback/Alert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import PartnerOffers from './lendingcollectview/PartnerOffers.vue'
import CollectedList, {type CollectedEntry} from './lendingcollectview/CollectedList.vue'
import {equipment, events as eventsApi, lending} from '@/api'
import type {AvailableInventoryEntry} from '@/api/lending'
import type {LineCheck, NeedCoverage} from '@/api/equipment'
import {useAsyncLoader} from '@/composables/useAsyncLoader'

const {t} = useI18n()
const route = useRoute()

const eventId = computed(() => (route.query.eventId ? Number(route.query.eventId) : null))
const date = computed(() => (typeof route.query.date === 'string' ? route.query.date : null))

const offers = ref<AvailableInventoryEntry[]>([])
const emptyReason = ref<string | null>(null)
const open = ref<NeedCoverage[]>([])
const entries = ref<CollectedEntry[]>([])
const checks = ref<LineCheck[]>([])
const sending = ref(false)
const sent = ref('')
const occasion = ref('')

const {loading, error, reload} = useAsyncLoader(async () => {
  const answer = await lending.listAvailable(date.value ? {from: date.value, to: date.value} : undefined)
  offers.value = answer.entries
  emptyReason.value = answer.emptyReason
  if (eventId.value && date.value) {
    open.value = (await equipment.coverage(eventId.value, date.value)).filter(line => line.missing > 0)
    occasion.value = (await eventsApi.getEvent(eventId.value)).name ?? ''
  }
})

onMounted(reload)

/** The line of an appointment's needs a picked offer would fill, matched by what it is called. */
function needFor(offer: AvailableInventoryEntry): number | null {
  const match = open.value.find(line => line.label === (offer.artName ?? offer.inventoryName))
  return match ? match.need.id : null
}

async function pick(offer: AvailableInventoryEntry) {
  const key = `${offer.stationId}-${offer.inventoryId}-${offer.artId ?? 'all'}`
  if (entries.value.some(entry => entry.key === key)) return
  entries.value.push({
    key,
    owningStationId: offer.stationId,
    stationName: offer.stationName,
    inventoryId: offer.inventoryId,
    inventoryName: offer.inventoryName,
    artId: offer.artId,
    label: offer.artName ?? offer.inventoryName,
    quantity: 1,
    needId: needFor(offer),
  })
  await recheck()
}

function remove(key: string) {
  entries.value = entries.value.filter(entry => entry.key !== key)
  recheck()
}

async function recheck() {
  if (!date.value || entries.value.length === 0) {
    checks.value = []
    return
  }
  const answer = await equipment.checkCollected(
      date.value,
      date.value,
      entries.value.map(entry => ({
        owningStationId: entry.owningStationId,
        inventoryId: entry.inventoryId,
        artId: entry.artId,
        quantity: entry.quantity,
        needId: entry.needId,
      })))
  checks.value = answer.lines
}

/**
 * Sends the list, one request per station. Availability is counted again first and what has moved is
 * shown, because nothing was held while the list was assembled.
 */
async function send() {
  if (!date.value) return
  sending.value = true
  try {
    await recheck()
    const stations = [...new Set(entries.value.map(entry => entry.owningStationId))]
    for (const stationId of stations) {
      await lending.createRequest({
        owningStationId: stationId,
        dateFrom: date.value,
        dateTo: date.value,
        eventId: eventId.value,
        eventDate: date.value,
        items: entries.value
            .filter(entry => entry.owningStationId === stationId)
            .map(entry => ({
              inventoryId: entry.inventoryId,
              itemId: null,
              artId: entry.artId,
              quantity: entry.quantity,
              needId: entry.needId,
            })),
      })
    }
    sent.value = t('lendingCollect.sentCount', {count: stations.length})
    entries.value = []
    checks.value = []
  } finally {
    sending.value = false
  }
}
</script>

<template>
  <ViewContent :title="t('pages.inventory-lending-collect.title')" :subtitle="t('pages.inventory-lending-collect.subtitle')">
    <Spinner v-if="loading" size="lg"/>
    <Alert v-if="error" variant="error">{{ error }}</Alert>
    <Alert v-if="sent" variant="success" data-testid="collected-sent">{{ sent }}</Alert>

    <NeutralContainer v-if="occasion" class="mb-4" data-testid="collect-occasion">
      {{ t('lendingCollect.forOccasion', {occasion, date: date ?? ''}) }}
    </NeutralContainer>

    <div class="grid gap-6 md:grid-cols-2">
      <NeutralContainer>
        <PartnerOffers :offers="offers" :empty-reason="emptyReason" @pick="pick"/>
      </NeutralContainer>
      <NeutralContainer>
        <CollectedList :entries="entries" :checks="checks" :sending="sending" @remove="remove" @send="send"/>
      </NeutralContainer>
    </div>
  </ViewContent>
</template>
