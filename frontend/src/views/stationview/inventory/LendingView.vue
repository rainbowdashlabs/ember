/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useInventoryRoutes} from '@/composables/useInventoryRoutes'
import {onMounted, ref, computed, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import Alert from '@/components/feedback/Alert.vue'
import AsyncSection from '@/components/feedback/AsyncSection.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import LendingRequestList from '@/views/stationview/inventory/lendingview/LendingRequestList.vue'
import ShopSection from '@/views/stationview/inventory/lendingview/ShopSection.vue'
import LendingTabs from '@/views/stationview/inventory/lendingview/LendingTabs.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import {StationPermission, type LendingRequestResponse} from '@/api/generated/schema'
import * as lending from '@/api/lending'
import {useSession} from '@/composables/useSession'
import {useAsyncLoader} from '@/composables/useAsyncLoader'

const routes = useInventoryRoutes()

const {t} = useI18n()
const router = useRouter()
const {loaded, hasPermission} = useSession()

const isLendingManager = computed(() => hasPermission(StationPermission.INVENTORY_LENDING_MANAGER))

const activeTab = ref<'offers' | 'requests'>('offers')

const requests = ref<LendingRequestResponse[]>([])
const requestSuccess = ref('')

const {
    loading: loadingRequests,
    failure: requestsFailure,
    reload: loadRequests,
} = useAsyncLoader(async () => {
    requests.value = await lending.listRequests()
}, {autoLoad: false})

const incoming = computed(() => requests.value.filter(r => r.isOwner))
const outgoing = computed(() => requests.value.filter(r => !r.isOwner))

onMounted(() => {
  if (loaded.value) loadRequests()
})

watch(loaded, (v) => {
  if (v) loadRequests()
})
</script>

<template>
  <ViewContent
      :title="t('pages.inventory-lending.title')"
      :subtitle="t('pages.inventory-lending.subtitle')"
  >
    <div class="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-2 mb-4">
      <ButtonRow>
        <PrimaryButton v-if="isLendingManager" :icon="['fas', 'calendar-xmark']" @click="router.push({name: routes.lendingBlocks})">
          {{ t('lending.blocks') }}
        </PrimaryButton>
        <SecondaryButton
            v-if="isLendingManager && routes.lendingShares"
            :icon="['fas', 'share-nodes']"
            data-testid="lending-shares-link"
            @click="router.push({name: routes.lendingShares})"
        >
          {{ t('lendingShare.overview') }}
        </SecondaryButton>
      </ButtonRow>
    </div>

    <LendingTabs v-model="activeTab" class="mb-4"/>

    <Alert v-if="requestSuccess" variant="success" class="mb-4">{{ requestSuccess }}</Alert>

    <ShopSection v-if="activeTab === 'offers' && loaded"/>

    <template v-if="activeTab === 'requests'">
      <AsyncSection :failure="requestsFailure" :loading="loadingRequests">
        <template v-if="isLendingManager">
          <SubHeader class="mt-2 mb-2">{{ t('lending.incoming') }}</SubHeader>
          <EmptyState v-if="incoming.length === 0" compact>{{ t('lending.noIncoming') }}</EmptyState>
          <LendingRequestList :entries="incoming" direction="incoming"/>
        </template>

        <SubHeader class="mt-6 mb-2">{{ t('lending.outgoing') }}</SubHeader>
        <EmptyState v-if="outgoing.length === 0" compact>{{ t('lending.noOutgoing') }}</EmptyState>
        <LendingRequestList :entries="outgoing" direction="outgoing"/>
      </AsyncSection>
    </template>
  </ViewContent>
</template>
