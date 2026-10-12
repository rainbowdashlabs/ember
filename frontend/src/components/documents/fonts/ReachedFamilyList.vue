/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {FontFamilyOption, FontStyle} from '@/api/generated/schema'
import FontSample from './FontSample.vue'

/**
 * The families the owner's templates also reach from further up, each with a line of sample text: the
 * default font every text naming none prints in first, then an association's for its stations, the
 * instance's and the built-in ones for everybody. They are used here, never changed; a family of the same
 * name at the owner itself takes their place.
 *
 * <p>The default font is no uploaded family and so never among the others, which left a font every
 * picker offers missing from the one page that lists the fonts.
 */
defineProps<{
  families: readonly FontFamilyOption[]
  /** The family of the default font, and the styles the editor can show it in. */
  defaultFamily: string
  defaultStyles: readonly FontStyle[]
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3" data-testid="font-reached">
    <SubHeader>{{ t('documentFonts.reachedTitle') }}</SubHeader>
    <MutedText size="sm" tag="p">{{ t('documentFonts.reachedHint') }}</MutedText>
    <ul class="divide-y divide-(--border)">
      <li v-if="defaultFamily" class="space-y-1 py-2" data-testid="font-reached-default">
        <span class="flex flex-wrap items-center gap-2">
          <span class="font-semibold">{{ defaultFamily }}</span>
          <SecondaryBadge>{{ t('documentFonts.origin.DEFAULT') }}</SecondaryBadge>
          <MutedText size="sm">{{ defaultStyles.map(style => t(`documentFonts.style.${style}`)).join(', ') }}</MutedText>
        </span>
        <FontSample :family="null" :version="defaultFamily" :label="t('documentFonts.sampleOf', {family: defaultFamily})"/>
      </li>
      <li v-for="family in families" :key="family.family" class="space-y-1 py-2">
        <span class="flex flex-wrap items-center gap-2">
          <span class="font-semibold">{{ family.family }}</span>
          <SecondaryBadge>{{ t(`documentFonts.origin.${family.origin}`) }}</SecondaryBadge>
          <MutedText size="sm">{{ family.styles.map(style => t(`documentFonts.style.${style}`)).join(', ') }}</MutedText>
        </span>
        <FontSample :family="family.family" :version="family.sample" :label="t('documentFonts.sampleOf', {family: family.family})"/>
      </li>
    </ul>
  </NeutralContainer>
</template>
