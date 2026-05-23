/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import type { ExchangeLogEntry, ExchangeStatusName } from '@/api/types'
import { exchanges } from '@/api'

const { t } = useI18n()

const model = defineModel<boolean>({ required: true })

const props = defineProps<{
  exchangeId: number | null
}>()

const emit = defineEmits<{
  error: [msg: string]
}>()

const logEntries = ref<ExchangeLogEntry[]>([])
const logLoading = ref(false)

function statusLabel(status: ExchangeStatusName): string {
  return t(`exchanges.status.${status}`)
}

function formatDate(dateStr: string): string {
  return new Date(dateStr).toLocaleDateString('de-DE', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  })
}

watch(model, async (open) => {
  if (open && props.exchangeId) {
    logLoading.value = true
    logEntries.value = []
    try {
      logEntries.value = await exchanges.getLogs(props.exchangeId)
    } catch {
      emit('error', t('common.error'))
    } finally {
      logLoading.value = false
    }
  }
})
</script>

<template>
  <Modal v-model="model">
    <div class="space-y-4">
      <SectionHeader>{{ t('exchanges.logTitle') }}</SectionHeader>
      <Spinner v-if="logLoading" size="md" />
      <div v-else-if="logEntries.length === 0" class="text-sm text-(--text-muted)">
        {{ t('exchanges.noLogs') }}
      </div>
      <div v-else class="space-y-2">
        <NeutralContainer v-for="entry in logEntries" :key="entry.id" class="text-sm space-y-1">
          <div class="flex items-center gap-2 flex-wrap">
            <span class="font-medium">{{ statusLabel(entry.oldStatus as ExchangeStatusName) }}</span>
            <font-awesome-icon :icon="['fas', 'arrow-right']" class="text-(--text-muted)" />
            <span class="font-medium">{{ statusLabel(entry.newStatus as ExchangeStatusName) }}</span>
          </div>
          <div class="text-(--text-muted)">
            {{ entry.changedByName }} &mdash; {{ formatDate(entry.changedAt) }}
          </div>
          <div v-if="entry.note">{{ entry.note }}</div>
        </NeutralContainer>
      </div>
      <div class="flex justify-end">
        <SecondaryButton @click="model = false">{{ t('common.close') }}</SecondaryButton>
      </div>
    </div>
  </Modal>
</template>
