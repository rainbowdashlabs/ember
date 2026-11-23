/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import type { ProcurementResponse } from '@/api/generated/schema'
import ProcurementEntryDetails from './ProcurementEntryDetails.vue'

const { t } = useI18n()

/** One order on the procurement page: what it is, and what may be done with it. */
defineProps<{
  entry: ProcurementResponse
  canManageProcurement: boolean
}>()

defineEmits<{
  fulfill: [id: number]
  fromStock: [entry: ProcurementResponse]
  delete: [id: number]
}>()

</script>

<template>
  <NeutralContainer data-testid="procurement-entry">
    <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
      <ProcurementEntryDetails :entry="entry"/>

      <ButtonRow v-if="canManageProcurement" class="shrink-0">
        <SecondaryButton
            v-if="!entry.fulfilledAt && entry.stock.length > 0"
            :icon="['fas', 'box']"
            data-testid="procurement-from-stock"
            @click="$emit('fromStock', entry)"
        >
          {{ t('procurement.fromStock') }}
        </SecondaryButton>
        <PrimaryButton
            v-if="!entry.fulfilledAt"
            :icon="['fas', 'check']"
            data-testid="procurement-fulfill"
            @click="$emit('fulfill', entry.id)"
        >
          {{ t('procurement.markFulfilled') }}
        </PrimaryButton>
        <DeleteButton @click="$emit('delete', entry.id)" />
      </ButtonRow>
    </div>
  </NeutralContainer>
</template>
