/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import FormLabel from '@/components/input/FormLabel.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import InventoryTabs from './clusterinventoryview/InventoryTabs.vue'
import DispatchItemList from './clusterdispatchview/DispatchItemList.vue'
import {clusterInventory, clusterStations} from '@/api'
import type {SendableItem} from '@/api/clusterInventory'
import type {ClusterStation} from '@/api/clusterStations'
import {useInventoryRoutes} from '@/composables/useInventoryRoutes'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useAsyncAction} from '@/composables/useAsyncAction'

/**
 * Sending gear out of the association's store to one of its stations.
 *
 * <p>Its own screen rather than an action on each piece, because the everyday case is many at once: a group
 * is kitted out, not a jacket. One movement carries the lot, so the station confirms one arrival rather than
 * twenty, and the association watches one chain rather than twenty.
 */
const {t} = useI18n()
const router = useRouter()
const routes = useInventoryRoutes()

const stations = ref<ClusterStation[]>([])
const items = ref<SendableItem[]>([])
const stationUid = ref('')
const picked = ref<number[]>([])
const reason = ref('')

const {loading, error, reload} = useAsyncLoader(async () => {
  ;[stations.value, items.value] = await Promise.all([
    clusterStations.listStations(),
    clusterInventory.listSendable(),
  ])
})

const canSend = computed(() => stationUid.value !== '' && picked.value.length > 0)

const {running, error: sendError, run: send} = useAsyncAction(async () => {
  await clusterInventory.dispatch(stationUid.value, picked.value, reason.value.trim())
  picked.value = []
  reason.value = ''
  await reload()
  await router.push({name: routes.movement ? 'cluster-inventory-movements' : routes.manage})
})
</script>

<template>
  <ViewContent :subtitle="t('pages.cluster-inventory-dispatch.subtitle')"
               :title="t('pages.cluster-inventory-dispatch.title')">
    <div class="space-y-6">
      <InventoryTabs/>

      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error || sendError" variant="error">{{ error || sendError }}</Alert>

      <template v-if="!loading">
        <EmptyState v-if="items.length === 0">{{ t('clusterInventory.dispatch.empty') }}</EmptyState>

        <template v-else>
          <NeutralContainer class="space-y-3" data-testid="dispatch-station">
            <SectionHeader>{{ t('clusterInventory.dispatch.stationTitle') }}</SectionHeader>
            <MutedText size="sm">{{ t('clusterInventory.dispatch.stationHint') }}</MutedText>
            <div class="space-y-1">
              <FormLabel>{{ t('clusterInventory.dispatch.stationLabel') }}</FormLabel>
              <SelectInput v-model="stationUid" class="w-72" data-testid="dispatch-station-select">
                <option value="" disabled>{{ t('clusterInventory.dispatch.stationPlaceholder') }}</option>
                <option v-for="station in stations" :key="station.uid" :value="station.uid">
                  {{ station.name }}
                </option>
              </SelectInput>
            </div>
          </NeutralContainer>

          <DispatchItemList v-model="picked" :items="items"/>

          <NeutralContainer class="space-y-3">
            <FormLabel>{{ t('clusterInventory.dispatch.reasonLabel') }}</FormLabel>
            <TextAreaInput v-model="reason" :placeholder="t('clusterInventory.dispatch.reasonPlaceholder')" :rows="2"
                           data-testid="dispatch-reason"/>
            <div class="flex justify-end">
              <PrimaryButton :disabled="running || !canSend" data-testid="dispatch-send" @click="send">
                {{ t('clusterInventory.dispatch.send', {count: picked.length}) }}
              </PrimaryButton>
            </div>
          </NeutralContainer>
        </template>
      </template>
    </div>
  </ViewContent>
</template>
