/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import QuotaFieldsInput from '@/views/adminview/adminstorageview/QuotaFieldsInput.vue'
import type {ClusterStationRoom, QuotaDimensions} from '@/api/clusterStorage'
import {QUOTA_FIELD_KEYS, emptyQuotaFields, fieldFromBytes, fieldToBytes, type QuotaFields} from '@/util/storage'

/**
 * What one station is granted, dimension by dimension.
 *
 * <p>A dimension left empty falls back to the association's defaults rather than to nothing, so granting a
 * station a total without saying anything about knowledge files is the ordinary case rather than a mistake.
 */
const props = defineProps<{
  modelValue: boolean
  station: ClusterStationRoom | null
  busy: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [open: boolean]
  save: [stationUid: string, room: QuotaDimensions]
  handBack: [stationUid: string]
}>()

const {t} = useI18n()

const fields = ref<QuotaFields>(emptyQuotaFields())

const DIMENSION_OF: Record<string, keyof QuotaDimensions> = {
  total: 'totalBytes',
  kb: 'kbBytes',
  board: 'boardBytes',
  images: 'imagesBytes',
  pages: 'pagesBytes',
  perFile: 'perFileBytes',
  perImage: 'perImageBytes',
}

watch(() => props.station, station => {
  const next = emptyQuotaFields()
  if (station) for (const key of QUOTA_FIELD_KEYS) next[key] = fieldFromBytes(station.granted[DIMENSION_OF[key]!])
  fields.value = next
}, {immediate: true})

function save() {
  if (!props.station) return
  emit('save', props.station.stationUid, {
    totalBytes: fieldToBytes(fields.value.total),
    kbBytes: fieldToBytes(fields.value.kb),
    boardBytes: fieldToBytes(fields.value.board),
    imagesBytes: fieldToBytes(fields.value.images),
    pagesBytes: fieldToBytes(fields.value.pages),
    perFileBytes: fieldToBytes(fields.value.perFile),
    perImageBytes: fieldToBytes(fields.value.perImage),
  })
}
</script>

<template>
  <Modal :model-value="props.modelValue" @update:model-value="emit('update:modelValue', $event)">
    <SectionHeader>{{ t('clusterStorage.grantRoomTo', {name: props.station?.stationName ?? ''}) }}</SectionHeader>
    <p class="text-sm text-(--text-muted) mt-2 mb-3">{{ t('clusterStorage.emptyMeansDefaults') }}</p>
    <QuotaFieldsInput :fields="fields"/>
    <div class="flex justify-between gap-2 mt-4">
      <SecondaryButton :disabled="props.busy || !props.station"
                       data-testid="station-room-hand-back"
                       @click="props.station && emit('handBack', props.station.stationUid)">
        {{ t('clusterStorage.handBack') }}
      </SecondaryButton>
      <div class="flex gap-2">
        <SecondaryButton @click="emit('update:modelValue', false)">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="props.busy" data-testid="station-room-save" @click="save">
          {{ t('common.save') }}
        </PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
