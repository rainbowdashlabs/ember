/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import AsyncSection from '@/components/feedback/AsyncSection.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import LendingShareRow from '@/views/stationview/inventory/lendingsharesview/LendingShareRow.vue'
import LendingShareModal from '@/components/lending/LendingShareModal.vue'
import * as lending from '@/api/lending'
import type {ShareDetail} from '@/api/lending'

const {t} = useI18n()

const shares = ref<ShareDetail[]>([])
const loading = ref(true)
const error = ref('')

const offered = computed(() => shares.value.filter(s => s.share.shareGrant === 'GRANT'))
const withheld = computed(() => shares.value.filter(s => s.share.shareGrant === 'WITHHOLD'))

const editing = ref<ShareDetail | null>(null)
const editorOpen = ref(false)

function labelOf(detail: ShareDetail): string {
  if (detail.share.itemId != null) {
    const internal = detail.itemInternalId ? ` (${detail.itemInternalId})` : ''
    return `${detail.itemName ?? t('common.unknown')}${internal}`
  }
  return detail.inventoryName ?? t('common.unknown')
}

function edit(detail: ShareDetail) {
  editing.value = detail
  editorOpen.value = true
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    shares.value = await lending.listShares()
  } catch {
    error.value = t('lendingShare.loadError')
  } finally {
    loading.value = false
  }
}

watch(editorOpen, (open) => {
  if (!open) editing.value = null
})

onMounted(load)
</script>

<template>
  <ViewContent
      :title="t('pages.inventory-lending-shares.title')"
      :subtitle="t('pages.inventory-lending-shares.subtitle')"
  >
    <AsyncSection
        :empty="shares.length === 0"
        :empty-message="t('lendingShare.nothingOffered')"
        :error="error"
        :loading="loading"
    >
      <MutedText class="mb-4 block">{{ t('lendingShare.overviewHint') }}</MutedText>

      <template v-if="offered.length > 0">
        <SubHeader class="mb-2">{{ t('lendingShare.offered') }}</SubHeader>
        <div class="flex flex-col gap-2 mb-6" data-testid="lending-shares-offered">
          <LendingShareRow
              v-for="detail in offered"
              :key="detail.share.id"
              :detail="detail"
              :label="labelOf(detail)"
              @edit="edit(detail)"
          />
        </div>
      </template>

      <template v-if="withheld.length > 0">
        <SubHeader class="mb-2">{{ t('lendingShare.withheld') }}</SubHeader>
        <div class="flex flex-col gap-2" data-testid="lending-shares-withheld">
          <LendingShareRow
              v-for="detail in withheld"
              :key="detail.share.id"
              :detail="detail"
              :label="labelOf(detail)"
              @edit="edit(detail)"
          />
        </div>
      </template>
    </AsyncSection>

    <LendingShareModal
        v-if="editing"
        v-model="editorOpen"
        :target="editing.share.itemId != null ? 'item' : 'inventory'"
        :target-id="editing.share.itemId ?? editing.share.inventoryId ?? 0"
        :target-name="labelOf(editing)"
        @saved="load"
    />
  </ViewContent>
</template>
