/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, useId} from 'vue'
import {useI18n} from 'vue-i18n'
import BareButton from '@/components/button/BareButton.vue'
import PieceThumb from '@/components/inventory/PieceThumb.vue'
import SearchInput from '@/components/input/text/SearchInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {RemotePiece} from '@/api/generated/schema'
import {offerLook} from '@/util/offerLook'

/**
 * The single pieces of a product, each free or not, narrowed by a filter over name and description.
 * Choosing one opens it in detail; the slot after each row takes what is done with it.
 */
const props = defineProps<{
  pieces: RemotePiece[]
  stationUid: string
  /** Whether days were chosen, which decides whether "free" means today or those days. */
  windowed: boolean
}>()

const emit = defineEmits<{
  open: [piece: RemotePiece]
}>()

defineSlots<{
  action(props: {piece: RemotePiece}): unknown
}>()

const {t} = useI18n()
const filterId = useId()
const filter = ref('')

const shown = computed(() => {
  const words = filter.value.trim().toLowerCase()
  if (!words) return props.pieces
  return props.pieces.filter(piece => `${piece.name} ${piece.description}`.toLowerCase().includes(words))
})

function freeLabel(piece: RemotePiece): string {
  if (props.windowed) return piece.free ? t('lendingProduct.freeInWindow') : t('lendingProduct.notFreeInWindow')
  return piece.free ? t('lendingProduct.freeToday') : t('lendingProduct.notFreeToday')
}
</script>

<template>
  <div class="space-y-2" data-testid="lending-product-pieces">
    <div v-if="pieces.length > 6" class="space-y-1">
      <FieldLabel :id="filterId">{{ t('lendingProduct.filterPieces') }}</FieldLabel>
      <SearchInput v-model="filter" :aria-labelledby="filterId"/>
    </div>
    <MutedText v-if="shown.length === 0" tag="p">{{ t('lendingProduct.noPieceMatches') }}</MutedText>
    <ul class="divide-y divide-(--border) rounded-theme border border-(--border)">
      <li v-for="piece in shown" :key="piece.itemId" class="flex flex-wrap items-center gap-3 p-3">
        <BareButton class="flex min-w-0 flex-1 items-center gap-3 text-left"
                    :aria-label="t('lendingProduct.details', {name: piece.name})" @click="emit('open', piece)">
          <PieceThumb v-bind="offerLook({stationId: stationUid, pictureKey: piece.pictureKey, icon: null,
                                         color: null, homogeneous: false}, 128)"/>
          <span class="min-w-0">
            <span class="block font-medium">{{ piece.name }}</span>
            <span class="block text-xs" :class="piece.free ? 'text-success' : 'text-(--text-muted)'">{{ freeLabel(piece) }}</span>
          </span>
        </BareButton>
        <slot name="action" :piece="piece"/>
      </li>
    </ul>
  </div>
</template>
