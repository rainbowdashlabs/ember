/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import SizeBadge from '@/components/badge/SizeBadge.vue'
import NoteEditor from '@/components/comment/NoteEditor.vue'
import {inventory} from '@/api'
import type {InventoryItem, InventoryItemHistory} from '@/api/types'
import {useSession} from '@/composables/useSession'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const {hasPermission} = useSession()

const itemId = computed(() => Number(route.params.id))
const item = ref<InventoryItem | null>(null)
const history = ref<InventoryItemHistory[]>([])
const loading = ref(true)
const error = ref('')

const isManager = computed(() => hasPermission('INVENTORY_MANAGER') || hasPermission('STATION_ADMINISTRATOR'))
const currentAssignment = computed(() => {
  const current = history.value.find(h => !h.returned)
  return current?.memberName || null
})

function formatDate(iso?: string | null): string {
  if (!iso) return '—'
  return new Date(iso).toLocaleDateString('de-DE', {day: '2-digit', month: '2-digit', year: 'numeric'})
}

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const [i, h] = await Promise.all([
      inventory.getItem(itemId.value),
      inventory.getItemHistory(itemId.value),
    ])
    item.value = i
    history.value = h
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <div class="flex items-center gap-2">
        <SecondaryButton :icon="['fas', 'arrow-left']" @click="router.back()"/>
        <SectionHeader>{{ t('itemDetail.title') }}</SectionHeader>
      </div>

      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading && item">
        <!-- Item metadata -->
        <NeutralContainer class="space-y-3">
          <SubHeader>{{ item.name }}</SubHeader>
          <div class="grid grid-cols-2 sm:grid-cols-4 gap-4 text-sm">
            <div v-if="item.internalId">
              <FieldLabel class="text-xs">{{ t('itemDetail.internalId') }}</FieldLabel>
              <span class="font-mono">{{ item.internalId }}</span>
            </div>
            <div v-if="item.sizeId">
              <FieldLabel class="text-xs">{{ t('itemDetail.size') }}</FieldLabel>
              <SizeBadge>{{ item.sizeId }}</SizeBadge>
            </div>
            <div>
              <FieldLabel class="text-xs">{{ t('itemDetail.status') }}</FieldLabel>
              <ErrorBadge v-if="item.lostAt">{{ t('profile.lostSince') }} {{ formatDate(item.lostAt) }}</ErrorBadge>
              <SuccessBadge v-else>{{ t('itemDetail.active') }}</SuccessBadge>
            </div>
            <div v-if="currentAssignment">
              <FieldLabel class="text-xs">{{ t('itemDetail.assignedTo') }}</FieldLabel>
              <span>{{ currentAssignment }}</span>
            </div>
          </div>
        </NeutralContainer>

        <!-- Assignment history -->
        <NeutralContainer class="space-y-3">
          <SubHeader>{{ t('itemDetail.history') }}</SubHeader>
          <div v-if="history.length === 0" class="text-sm text-(--text-muted)">{{ t('itemDetail.noHistory') }}</div>
          <table v-else class="w-full text-sm">
            <thead>
              <tr class="text-xs text-(--text-muted) border-b border-(--border)">
                <th class="p-2 text-left">{{ t('itemDetail.member') }}</th>
                <th class="p-2 text-left">{{ t('itemDetail.givenOut') }}</th>
                <th class="p-2 text-left">{{ t('itemDetail.returned') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="h in history" :key="h.id" class="border-b border-(--border) last:border-0">
                <td class="p-2">{{ h.memberName || '—' }}</td>
                <td class="p-2 text-(--text-muted)">{{ formatDate(h.givenOut) }}</td>
                <td class="p-2 text-(--text-muted)">{{ h.returned ? formatDate(h.returned) : t('itemDetail.current') }}</td>
              </tr>
            </tbody>
          </table>
        </NeutralContainer>

        <!-- Manager notes -->
        <NeutralContainer v-if="isManager">
          <NoteEditor :entity-type="'ITEM'" :entity-id="itemId"/>
        </NeutralContainer>
      </template>
    </div>
  </ViewContent>
</template>
