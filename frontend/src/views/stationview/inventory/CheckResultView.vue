/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import type { CheckDetail } from '@/api/types'
import { inventoryCheck } from '@/api'
import { useConfigPanel } from '@/composables/useConfigPanel'
import { formatDateTime } from '@/util/format'
import CheckResultItemCard from '@/views/stationview/inventory/checkresultview/CheckResultItemCard.vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const memberId = computed(() => Number(route.params.memberId))
const memberName = computed(() => (route.query.name as string) ?? '')
const {config: detail, loading, error} = useConfigPanel<CheckDetail | null>({
  initial: null,
  fetch: () => inventoryCheck.getLastCheck(memberId.value),
})

function goBack() {
  router.push({ name: 'inventory-checks' })
}
</script>

<template>
  <ViewContent
      :title="t('pages.inventory-check-result.title')"
      :subtitle="t('pages.inventory-check-result.subtitle')"
  >
    <div class="space-y-6">
      <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
        <div>
          <p v-if="memberName" class="text-sm text-(--text-muted)">{{ t('inventory.check.lastResult') }}</p>
        </div>
        <SecondaryButton :icon="['fas', 'chevron-left']" @click="goBack">
          {{ t('inventory.check.backToOverview') }}
        </SecondaryButton>
      </div>

      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading && detail">
        <NeutralContainer>
          <div class="text-sm text-(--text-muted)">
            {{ formatDateTime(detail.check.checkedAt) || '-' }}
            &middot; {{ t('inventory.check.checkedBy') }}: {{ detail.checkerFirstName }} {{ detail.checkerLastName }}
          </div>
        </NeutralContainer>

        <EmptyState v-if="detail.items.length === 0">{{ t('inventory.check.noLastCheck') }}</EmptyState>

        <div class="space-y-2">
          <CheckResultItemCard
            v-for="item in detail.items"
            :key="item.id"
            :item="item"
          />
        </div>
      </template>

      <EmptyState v-if="!loading && !detail && !error">{{ t('inventory.check.noLastCheck') }}</EmptyState>
    </div>
  </ViewContent>
</template>
