/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useInventoryRoutes} from '@/composables/useInventoryRoutes'
import {onMounted, ref, computed, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import StationBadge from '@/components/badge/StationBadge.vue'
import * as lending from '@/api/lending'
import {useSession} from '@/composables/useSession'
import {useAsyncAction} from '@/composables/useAsyncAction'
import FieldLabel from '@/components/typography/FieldLabel.vue'

const routes = useInventoryRoutes()

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const {loaded} = useSession()

const inventoryId = Number(route.query.inventoryId)
const stationId = String(route.query.stationId ?? '')
const stationName = String(route.query.stationName || '')

const dateFrom = ref('')
const dateTo = ref('')
const quantity = ref(1)
const note = ref('')

const availableItems = ref<lending.AvailableInventoryEntry[]>([])
const loadingItems = ref(true)
const itemsError = ref('')

const selectedEntry = computed(() =>
    availableItems.value.find(e => e.inventoryId === inventoryId && e.stationId === stationId),
)

const maxQuantity = computed(() => selectedEntry.value?.availableCount ?? 1)
const inventoryName = computed(() => selectedEntry.value?.inventoryName ?? '')

async function loadItems() {
  loadingItems.value = true
  itemsError.value = ''
  try {
    availableItems.value = (await lending.listAvailable()).entries
  } catch {
    itemsError.value = t('lending.loadError')
  } finally {
    loadingItems.value = false
  }
}

const {running: submitting, error: submitError, run: handleSubmit} = useAsyncAction(async () => {
  if (!dateFrom.value) return
  const result = await lending.createRequest({
    owningStationId: stationId,
    dateFrom: dateFrom.value,
    dateTo: dateTo.value || null,
    items: [{inventoryId, quantity: quantity.value}],
  })
  await router.push({name: routes.lendingRequest, params: {id: result.request.id}})
}, {formatError: () => t('lending.createError')})

onMounted(() => {
  if (loaded.value) loadItems()
})

watch(loaded, (v) => {
  if (v) loadItems()
})
</script>

<template>
  <ViewContent
      :title="t('pages.inventory-lending-create.title')"
      :subtitle="t('pages.inventory-lending-create.subtitle')"
  >
    <SecondaryButton :icon="['fas', 'chevron-left']" class="mb-4" @click="router.push({name: routes.lending})">
      {{ t('lending.backToList') }}
    </SecondaryButton>

    <SectionHeader class="mb-4">{{ t('lending.createRequest') }}</SectionHeader>

    <Spinner v-if="loadingItems"/>
    <Alert v-else-if="itemsError" variant="error">{{ itemsError }}</Alert>

    <template v-else>
      <!-- Selected inventory and station -->
      <NeutralContainer class="mb-4">
        <div class="flex items-center gap-2 flex-wrap">
          <span class="font-medium text-lg">{{ inventoryName }}</span>
          <StationBadge :station-name="stationName"/>
        </div>
        <span v-if="selectedEntry" class="text-sm text-[var(--text-muted)]">
          {{ selectedEntry.availableCount }} {{ t('lending.available') }}
        </span>
      </NeutralContainer>

      <Alert v-if="submitError" variant="error" class="mb-4">{{ submitError }}</Alert>

      <div class="flex flex-col gap-4">
        <!-- Date range -->
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <div>
            <FieldLabel class="mb-1">{{ t('lending.dateFrom') }}</FieldLabel>
            <DateInput v-model="dateFrom"/>
          </div>
          <div>
            <FieldLabel class="mb-1">{{ t('lending.dateTo') }}</FieldLabel>
            <DateInput v-model="dateTo"/>
          </div>
        </div>

        <!-- Quantity -->
        <div>
          <FieldLabel class="mb-1">{{ t('lending.quantity') }}</FieldLabel>
          <NumberInput v-model="quantity" :min="1" :max="maxQuantity"/>
        </div>

        <!-- Note -->
        <div>
          <FieldLabel class="mb-1">{{ t('lending.note') }}</FieldLabel>
          <TextAreaInput v-model="note" :placeholder="t('lending.notePlaceholder')" :rows="3"/>
        </div>

        <!-- Submit -->
        <div class="flex justify-end gap-2 mt-2">
          <SecondaryButton @click="router.push({name: routes.lending})">
            {{ t('common.cancel') }}
          </SecondaryButton>
          <PrimaryButton :icon="['fas', 'paper-plane']" :disabled="submitting || !dateFrom" @click="handleSubmit">
            {{ t('lending.sendRequest') }}
          </PrimaryButton>
        </div>
      </div>
    </template>
  </ViewContent>
</template>
