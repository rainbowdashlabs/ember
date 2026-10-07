/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import EmptyHint from '@/components/typography/EmptyHint.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import type {DocumentFontView, FontFamilyOption} from '@/api/generated/schema'
import FontFileRow from './FontFileRow.vue'
import FontPreview from './FontPreview.vue'
import {groupByFamily, reachedFamily} from './fontOptions'

/**
 * The owner's own fonts, one block per family with a line of sample text and a row per style. A font
 * is deleted from its row, where its web version is also uploaded, replaced and removed; the server
 * refuses deleting a font while a template in use prints with it.
 */
const props = defineProps<{
  fonts: readonly DocumentFontView[]
  /** Every family the owner reaches, its own among them, which names the version of each sample. */
  reachable: readonly FontFamilyOption[]
  /** The family a text naming none prints in, as the owner's font list names it. */
  defaultFamily: string
}>()

const emit = defineEmits<{
  remove: [font: DocumentFontView]
  web: [font: DocumentFontView]
  removeWeb: [font: DocumentFontView]
}>()

const {t} = useI18n()

const families = computed(() => groupByFamily(props.fonts))
</script>

<template>
  <NeutralContainer class="space-y-4" data-testid="font-list">
    <SubHeader>{{ t('documentFonts.ownTitle') }}</SubHeader>
    <EmptyHint v-if="families.length === 0">{{ t('documentFonts.none') }}</EmptyHint>
    <section v-for="group in families" :key="group.family" class="space-y-2" data-testid="font-family">
      <p class="font-semibold">{{ group.family }}</p>
      <FontPreview :family="reachedFamily(reachable, group.family)" :default-family="defaultFamily"/>
      <ul class="divide-y divide-(--border)">
        <FontFileRow v-for="font in group.fonts" :key="font.id" :font="font" @remove="emit('remove', font)"
                     @web="emit('web', font)" @remove-web="emit('removeWeb', font)"/>
      </ul>
    </section>
  </NeutralContainer>
</template>
