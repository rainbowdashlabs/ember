/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
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

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const {loaded} = useSession()

const inventoryId = Number(route.query.inventoryId)
const stationId = String(route.query.stationId ?? '')
const stationName = String(route.query.stationName || '')

// Form state
const dateFrom = ref('')
const dateTo = ref('')
const quantity = ref(1)
const note = ref('')
const submitting = ref(false)
const submitError = ref('')

// Available items from partner
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
    availableItems.value = await lending.listAvailable()
  } catch {
    itemsError.value = t('lending.loadError')
  } finally {
    loadingItems.value = false
  }
}

async function handleSubmit() {
  if (!dateFrom.value) return
  submitting.value = true
  submitError.value = ''
  try {
    const result = await lending.createRequest({
      owningStationId: stationId,
      dateFrom: dateFrom.value,
      dateTo: dateTo.value || null,
      items: [{inventoryId, quantity: quantity.value}],
    })
    await router.push({name: 'inventory-lending-request', params: {id: result.request.id}})
  } catch {
    submitError.value = t('lending.createError')
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  if (loaded.value) loadItems()
})

watch(loaded, (v) => {
  if (v) loadItems()
})
</script>

<template>
  <ViewContent>
    <SecondaryButton class="mb-4" @click="router.push({name: 'inventory-lending'})">
      <font-awesome-icon :icon="['fas', 'chevron-left']" class="mr-1"/>
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
            <label class="text-sm font-medium mb-1 block">{{ t('lending.dateFrom') }}</label>
            <DateInput v-model="dateFrom"/>
          </div>
          <div>
            <label class="text-sm font-medium mb-1 block">{{ t('lending.dateTo') }}</label>
            <DateInput v-model="dateTo"/>
          </div>
        </div>

        <!-- Quantity -->
        <div>
          <label class="text-sm font-medium mb-1 block">{{ t('lending.quantity') }}</label>
          <NumberInput v-model="quantity" :min="1" :max="maxQuantity"/>
        </div>

        <!-- Note -->
        <div>
          <label class="text-sm font-medium mb-1 block">{{ t('lending.note') }}</label>
          <TextAreaInput v-model="note" :placeholder="t('lending.notePlaceholder')" :rows="3"/>
        </div>

        <!-- Submit -->
        <div class="flex justify-end gap-2 mt-2">
          <SecondaryButton @click="router.push({name: 'inventory-lending'})">
            {{ t('common.cancel') }}
          </SecondaryButton>
          <PrimaryButton :disabled="submitting || !dateFrom" @click="handleSubmit">
            <font-awesome-icon :icon="['fas', 'paper-plane']" class="mr-1"/>
            {{ t('lending.sendRequest') }}
          </PrimaryButton>
        </div>
      </div>
    </template>
  </ViewContent>
</template>
