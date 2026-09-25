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
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import Alert from '@/components/feedback/Alert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import PartnerOffers from './lendingcollectview/PartnerOffers.vue'
import CollectedList, {type CollectedEntry} from './lendingcollectview/CollectedList.vue'
import {equipment, events as eventsApi, lending} from '@/api'
import type {AvailableInventoryEntry} from '@/api/lending'
import type {LineCheck, NeedCoverage} from '@/api/equipment'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {describeFailure, type Failure} from '@/util/failure'

const {t} = useI18n()
const route = useRoute()

const eventId = computed(() => (route.query.eventId ? Number(route.query.eventId) : null))
const date = computed(() => (typeof route.query.date === 'string' ? route.query.date : null))

const offers = ref<AvailableInventoryEntry[]>([])
const emptyReason = ref<string | null>(null)
const open = ref<NeedCoverage[]>([])
const entries = ref<CollectedEntry[]>([])
const checks = ref<LineCheck[]>([])
const sent = ref('')
const occasion = ref('')

/**
 * The appointment the gear is being gathered for, at the head of the page. Collecting for two of
 * them is two tabs that would otherwise read the same word. The plain wording stands while the
 * appointment loads and where the page was opened without one.
 */
const pageTitle = computed(() => (occasion.value
    ? t('pages.inventory-lending-collect.titleNamed', {name: occasion.value})
    : t('pages.inventory-lending-collect.title')))

const {loading, failure, reload} = useAsyncLoader(async () => {
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

/** Counts again what the picked lines are still worth, which nothing has been holding meanwhile. */
async function refreshChecks() {
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

const {failure: recheckFailure, run: recheck} = useAsyncAction(refreshChecks)

const sending = ref(false)
const sendFailure = ref<Failure | null>(null)

/**
 * Sends the list, one request per station. Availability is counted again first and what has moved is
 * shown, because nothing was held while the list was assembled.
 *
 * <p>One request per station means a failure halfway leaves the stations before it already asked. The
 * server answering the fourth request knows nothing of the three that went through, so the count is
 * kept here and said out loud: a reader who presses send again otherwise asks three stations twice for
 * the same gear, and somebody at each of them has to work out which of the two to refuse.
 */
async function send() {
  if (!date.value) return
  sending.value = true
  sendFailure.value = null
  sent.value = ''

  const stations = [...new Set(entries.value.map(entry => entry.owningStationId))]
  let reached = 0
  try {
    await refreshChecks()
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
      reached++
    }
  } catch (e) {
    const described = describeFailure(e, t)
    sendFailure.value = reached > 0
        ? {...described, message: t('lendingCollect.sentPartly', {count: reached})}
        : described
    return
  } finally {
    sending.value = false
  }

  sent.value = t('lendingCollect.sentCount', {count: stations.length})
  entries.value = []
  checks.value = []
}

const actionFailure = computed(() => sendFailure.value ?? recheckFailure.value)

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

</script>

<template>
  <ViewContent :title="pageTitle" :subtitle="t('pages.inventory-lending-collect.subtitle')">
    <Spinner v-if="loading" size="lg"/>
    <FailureAlert :failure="failure"/>
    <FailureAlert :failure="actionFailure" data-testid="collected-error"/>
    <Alert v-if="!date" variant="info" data-testid="collect-no-date">{{ t('lendingCollect.noDate') }}</Alert>
    <Alert v-if="sent" variant="success" data-testid="collected-sent">{{ sent }}</Alert>

    <NeutralContainer v-if="occasion" class="mb-4" data-testid="collect-occasion">
      {{ t('lendingCollect.forOccasion', {occasion, date: date ?? ''}) }}
    </NeutralContainer>

    <div class="grid gap-6 md:grid-cols-2">
      <NeutralContainer>
        <PartnerOffers :offers="offers" :empty-reason="emptyReason" @pick="pick"/>
      </NeutralContainer>
      <NeutralContainer>
        <CollectedList
            :entries="entries"
            :checks="checks"
            :sending="sending"
            :can-send="!!date"
            @remove="remove"
            @send="send"
        />
      </NeutralContainer>
    </div>
  </ViewContent>
</template>
