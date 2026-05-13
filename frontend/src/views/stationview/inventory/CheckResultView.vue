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
import SectionHeader from '@/components/typography/SectionHeader.vue'
import type { CheckDetail } from '@/api/types'
import { inventoryCheck } from '@/api'

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
        <SecondaryButton @click="goBack">
          <font-awesome-icon :icon="['fas', 'chevron-left']" class="mr-1" />
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

        <div v-if="detail.items.length === 0" class="text-center text-(--text-muted) py-8">
          {{ t('inventory.check.noLastCheck') }}
        </div>

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
                  <span class="font-normal text-(--text-muted)">[{{ item.sizeName ?? t('common.unisize') }}]</span>
                </div>
                <div class="text-xs text-(--text-muted)">
                  {{ item.inventoryName }}
                  <template v-if="item.internalId"> &middot; {{ item.internalId }}</template>
                </div>
              </div>
              <span class="inline-block self-start rounded-full px-2.5 py-0.5 text-xs font-medium shrink-0"
                :class="{
                  'bg-success/15 text-success': item.result === 'CONFIRMED',
                  'bg-info/15 text-info-accent': item.result === 'NOT_IN_POSSESSION',
                  'bg-error/15 text-error': item.result === 'LOST',
                }"
              >
                {{ resultLabel(item.result) }}
              </span>
            </div>
            <p v-if="item.note" class="text-sm text-(--text-muted) mt-1">{{ item.note }}</p>
          </NeutralContainer>
        </div>
      </template>

      <div v-if="!loading && !detail && !error" class="text-center text-(--text-muted) py-8">
        {{ t('inventory.check.noLastCheck') }}
      </div>
    </div>
  </ViewContent>
</template>
