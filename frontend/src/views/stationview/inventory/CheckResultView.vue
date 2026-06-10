/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import type { CheckDetail } from '@/api/types'
import { inventoryCheck } from '@/api'
import SizeBadge from '@/components/badge/SizeBadge.vue'
import MutedText from '@/components/typography/MutedText.vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const memberId = computed(() => Number(route.params.memberId))
const detail = ref<CheckDetail | null>(null)
const loading = ref(true)
const error = ref('')
const memberName = ref('')

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    detail.value = await inventoryCheck.getLastCheck(memberId.value)
    // Get member name from the overview (passed via query param)
    memberName.value = (route.query.name as string) ?? ''
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

function formatDate(dateStr?: string | null): string {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString('de-DE')
}

function resultLabel(result: string): string {
  switch (result) {
    case 'CONFIRMED': return t('inventory.check.resultConfirmed')
    case 'NOT_IN_POSSESSION': return t('inventory.check.resultNotInPossession')
    case 'LOST': return t('inventory.check.resultLost')
    default: return result
  }
}

function resultClass(result: string): string {
  switch (result) {
    case 'CONFIRMED': return 'bg-success/10 border-success'
    case 'NOT_IN_POSSESSION': return 'bg-info/10 border-info'
    case 'LOST': return 'bg-error/10 border-error'
    default: return ''
  }
}

function goBack() {
  router.push({ name: 'inventory-checks' })
}

onMounted(loadData)
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
        <div>
          <SectionHeader>{{ memberName || t('inventory.check.lastResult') }}</SectionHeader>
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
            {{ formatDate(detail.check.checkedAt) }}
            &middot; {{ t('inventory.check.checkedBy') }}: {{ detail.checkerFirstName }} {{ detail.checkerLastName }}
          </div>
        </NeutralContainer>

        <EmptyState v-if="detail.items.length === 0">{{ t('inventory.check.noLastCheck') }}</EmptyState>

        <!-- Card layout for mobile, works well on desktop too -->
        <div class="space-y-2">
          <NeutralContainer
            v-for="item in detail.items"
            :key="item.id"
            class="border-l-4 transition-all"
            :class="resultClass(item.result)"
          >
            <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
              <div class="flex-1 min-w-0">
                <div v-if="item.itemName" class="font-medium text-sm">
                  {{ item.itemName }}
                  <SizeBadge>{{ item.sizeName ?? t('common.unisize') }}</SizeBadge>
                </div>
                <div class="text-xs text-(--text-muted)">
                  {{ item.inventoryName }}
                  <template v-if="item.internalId"> &middot; {{ item.internalId }}</template>
                </div>
              </div>
              <SuccessBadge v-if="item.result === 'CONFIRMED'" class="self-start shrink-0">
                {{ resultLabel(item.result) }}
              </SuccessBadge>
              <InfoBadge v-else-if="item.result === 'NOT_IN_POSSESSION'" class="self-start shrink-0">
                {{ resultLabel(item.result) }}
              </InfoBadge>
              <ErrorBadge v-else-if="item.result === 'LOST'" class="self-start shrink-0">
                {{ resultLabel(item.result) }}
              </ErrorBadge>
            </div>
            <MutedText tag="p" size="sm" class="mt-1" v-if="item.note">{{ item.note }}</MutedText>
          </NeutralContainer>
        </div>
      </template>

      <EmptyState v-if="!loading && !detail && !error">{{ t('inventory.check.noLastCheck') }}</EmptyState>
    </div>
  </ViewContent>
</template>
