/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import Modal from '@/components/feedback/Modal.vue'
import PieceThumb from '@/components/inventory/PieceThumb.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import type {RemotePiece} from '@/api/generated/schema'
import {offerLook} from '@/util/offerLook'
import AvailabilityCalendar from './AvailabilityCalendar.vue'
import ProductDescription from './ProductDescription.vue'

/** One single piece in detail: its photo, its whole description and its own calendar. */
const open = defineModel<boolean>({required: true})
const props = defineProps<{
  piece: RemotePiece
  stationUid: string
  inventoryId: number
  dateFrom: string
  dateTo: string
}>()

const emit = defineEmits<{
  pick: [from: string, to: string]
}>()

const look = computed(() => offerLook({
  stationId: props.stationUid,
  pictureKey: props.piece.pictureKey,
  icon: null,
  color: null,
  homogeneous: false,
}, 512))
const rows = computed(() => [{key: 'piece', label: props.piece.name}])
</script>

<template>
  <Modal v-model="open" size="lg">
    <div class="space-y-4">
      <div class="flex aspect-[4/3] items-center justify-center overflow-hidden rounded-theme bg-bg-light-accent/40 dark:bg-bg-dark-accent/40">
        <PieceThumb v-bind="look" fit="contain"/>
      </div>
      <SectionHeader>{{ piece.name }}</SectionHeader>
      <ProductDescription :text="piece.description"/>
      <AvailabilityCalendar
          :date-from="dateFrom"
          :date-to="dateTo"
          :station-uid="stationUid"
          :inventory-id="inventoryId"
          :item-id="piece.itemId"
          :rows="rows"
          @pick="(from, to) => emit('pick', from, to)"
      />
    </div>
  </Modal>
</template>
