/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ExportFieldPicker from '@/components/export/ExportFieldPicker.vue'
import type {ExportFieldOption} from '@/composables/useExport'

/**
 * What stands above the queue: starting a movement, and making a sheet out of the rows.
 *
 * <p>Exporting is a mode rather than a press. What the filters leave standing is a starting point and
 * not the answer: a sheet to walk a shelf with is usually a handful of those rows, so the ticks come
 * first and the download second.
 */
const props = defineProps<{
  /** Whether rows are being ticked, which is the only time the download and the ticks are offered. */
  picking: boolean
  /** Whether every row on screen is ticked, which is what the one button switches between. */
  allPicked: boolean
  pickedCount: number
  busy: boolean
  /** Whether this reader may export at all, which is the queue's own right. */
  canExport: boolean
  /** The profile fields a sheet can carry beside the names, and the ones it is carrying. */
  fieldOptions: ExportFieldOption[]
  pickedFields: Set<string>
}>()

const emit = defineEmits<{
  start: []
  cancel: []
  toggleAll: []
  download: []
  create: []
  toggleField: [key: string]
}>()

const {t} = useI18n()
</script>

<template>
  <div class="space-y-2">
    <ButtonRow align="end">
      <template v-if="props.picking">
        <SecondaryButton data-testid="movement-export-all" @click="emit('toggleAll')">
          {{ props.allPicked ? t('movements.queue.selectNone') : t('movements.queue.selectAll') }}
        </SecondaryButton>
        <SecondaryButton data-testid="movement-export-cancel" @click="emit('cancel')">
          {{ t('common.cancel') }}
        </SecondaryButton>
        <PrimaryButton
            :disabled="props.busy || props.pickedCount === 0"
            :icon="['fas', 'download']"
            data-testid="movement-export-download"
            @click="emit('download')"
        >
          {{ props.busy
            ? t('common.loading')
            : t('movements.queue.downloadCount', {count: props.pickedCount}) }}
        </PrimaryButton>
      </template>
      <template v-else>
        <SecondaryButton v-if="props.canExport" :icon="['fas', 'file-export']" data-testid="movement-export"
                         @click="emit('start')">
          {{ t('movements.queue.export') }}
        </SecondaryButton>
        <PrimaryButton :icon="['fas', 'plus']" data-testid="movement-create" @click="emit('create')">
          {{ t('movements.queue.create') }}
        </PrimaryButton>
      </template>
    </ButtonRow>

    <ExportFieldPicker
        v-if="props.picking && props.fieldOptions.length > 0"
        :label="t('movements.queue.exportFieldsHint')"
        :options="props.fieldOptions"
        :selected="props.pickedFields"
        boxed
        layout="inline"
        @toggle="key => emit('toggleField', String(key))"
    />
  </div>
</template>
