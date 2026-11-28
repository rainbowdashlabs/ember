/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import PieceThumb from '@/components/inventory/PieceThumb.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {pictureUrl} from '@/api/inventoryPictures'
import {glyphFor} from '@/util/glyph'
import ShareControl from './ShareControl.vue'
import type {CatalogRow} from './catalogRows'

/**
 * One row of the offer catalog: selected for bulk changes, its photo, name and what it counts, who it
 * is offered to, and its text. A row the partner looked through does not see is greyed out.
 */
const props = defineProps<{
  row: CatalogRow
  /** How many pieces the row stands for, shown beside its name. */
  count?: number
  editable: boolean
  /** Whether the row is only shown, as in the help centre. */
  readonly?: boolean
}>()

const selected = defineModel<boolean>('selected', {required: true})

const emit = defineEmits<{
  edit: []
  changed: []
}>()

const {t} = useI18n()
const look = computed(() => ({
  pictureSrc: props.row.pictureKey ? pictureUrl(props.row.pictureKey, 128) : null,
  glyph: glyphFor({icon: null, color: null, homogeneous: props.row.target !== 'item'}),
}))
const hidden = computed(() => props.row.visible === false)
const nested = computed(() => props.row.depth > 0)
const INDENT = ['', 'pl-6', 'pl-12'] as const
</script>

<template>
  <li class="flex flex-wrap items-center gap-3 py-2" :class="[INDENT[row.depth] ?? 'pl-12', hidden ? 'opacity-50' : '']"
      :data-hidden="hidden || undefined" data-testid="lending-offer-row">
    <CheckboxInput v-if="!readonly" v-model="selected" :aria-label="t('lendingOffer.select', {name: row.name})"/>
    <PieceThumb v-bind="look"/>
    <div class="min-w-0 flex-1">
      <p :class="nested ? '' : 'font-medium'">{{ row.name }} <MutedText v-if="row.detail">({{ row.detail }})</MutedText></p>
      <MutedText v-if="count != null" tag="p">{{ count === 1 ? t('lendingOffer.pieceOne') : t('lendingOffer.pieces', {count}) }}</MutedText>
      <div class="flex flex-wrap gap-1">
        <InfoBadge v-if="!nested && !row.description.trim()">{{ t('lendingOffer.noDescription') }}</InfoBadge>
        <InfoBadge v-if="!nested && !row.pictureKey">{{ t('lendingOffer.noPicture') }}</InfoBadge>
        <MutedText v-if="hidden">{{ t('lendingOffer.hiddenFromPartner') }}</MutedText>
      </div>
    </div>
    <ShareControl :target="row.target" :target-id="row.id" :target-name="row.name" :share="row.share"
                  :under-kind="row.target === 'item' && row.artId != null" :readonly="readonly" @changed="emit('changed')"/>
    <SecondaryButton v-if="editable && !readonly" :icon="['fas', 'pen']" :aria-label="t('lendingOffer.editFor', {name: row.name})" @click="emit('edit')">
      {{ t('lendingOffer.edit') }}
    </SecondaryButton>
  </li>
</template>
