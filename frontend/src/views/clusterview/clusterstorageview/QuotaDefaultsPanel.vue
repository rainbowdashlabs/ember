/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Modal from '@/components/feedback/Modal.vue'
import QuotaFieldsInput from '@/views/adminview/adminstorageview/QuotaFieldsInput.vue'
import type {QuotaDimensions} from '@/api/clusterStorage'
import {
  QUOTA_FIELD_KEYS, QUOTA_FIELD_LABELS, emptyQuotaFields, fieldFromBytes, fieldToBytes, formatBytes,
  type QuotaFields,
} from '@/util/storage'

/**
 * What the association gives a station it has granted nothing of its own.
 *
 * <p>Not weighed against the pool: a default is not a promise to any one station, and what the pool bounds is
 * what was actually granted. A dimension left empty is one the association is not deciding, and whatever the
 * instance says about it stands.
 */
const props = defineProps<{
  defaults: QuotaDimensions
  busy: boolean
}>()

const emit = defineEmits<{ save: [defaults: QuotaDimensions] }>()

const {t} = useI18n()

const showModal = ref(false)
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

function readFields() {
  const next = emptyQuotaFields()
  for (const key of QUOTA_FIELD_KEYS) next[key] = fieldFromBytes(props.defaults[DIMENSION_OF[key]!])
  fields.value = next
}

watch(() => props.defaults, readFields, {immediate: true, deep: true})

function open() {
  readFields()
  showModal.value = true
}

function save() {
  emit('save', {
    totalBytes: fieldToBytes(fields.value.total),
    kbBytes: fieldToBytes(fields.value.kb),
    boardBytes: fieldToBytes(fields.value.board),
    imagesBytes: fieldToBytes(fields.value.images),
    pagesBytes: fieldToBytes(fields.value.pages),
    perFileBytes: fieldToBytes(fields.value.perFile),
    perImageBytes: fieldToBytes(fields.value.perImage),
  })
  showModal.value = false
}
</script>

<template>
  <NeutralContainer class="space-y-3">
    <SectionHeader>{{ t('clusterStorage.defaultsTitle') }}</SectionHeader>
    <p class="text-sm text-(--text-muted)">{{ t('clusterStorage.defaultsHint') }}</p>

    <dl class="grid grid-cols-2 sm:grid-cols-4 gap-2 text-sm" data-testid="cluster-defaults">
      <div v-for="key in QUOTA_FIELD_KEYS" :key="key">
        <dt class="text-(--text-muted)">{{ t(QUOTA_FIELD_LABELS[key]) }}</dt>
        <dd v-if="props.defaults[DIMENSION_OF[key]!] !== null">
          {{ formatBytes(props.defaults[DIMENSION_OF[key]!] ?? 0) }}
        </dd>
        <dd v-else class="text-(--text-muted)">{{ t('clusterStorage.leftToInstance') }}</dd>
      </div>
    </dl>

    <SecondaryButton :disabled="props.busy" data-testid="cluster-defaults-edit" @click="open">
      {{ t('clusterStorage.editDefaults') }}
    </SecondaryButton>

    <Modal v-model="showModal">
      <SectionHeader>{{ t('clusterStorage.defaultsTitle') }}</SectionHeader>
      <p class="text-sm text-(--text-muted) mt-2 mb-3">{{ t('clusterStorage.emptyMeansInstance') }}</p>
      <QuotaFieldsInput :fields="fields"/>
      <div class="flex justify-end gap-2 mt-4">
        <SecondaryButton @click="showModal = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton data-testid="cluster-defaults-save" @click="save">{{ t('common.save') }}</PrimaryButton>
      </div>
    </Modal>
  </NeutralContainer>
</template>
