/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.categories.title')" :subtitle="t('helpCenter.categories.subtitle')">
    <HelpSection :title="t('helpCenter.categories.whatIs')">
      <p>{{ t('helpCenter.categories.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.categories.howTo')">
      <p>{{ t('helpCenter.categories.howToText') }}</p>
    </HelpSection>

    <!-- Dummy: Category list -->
    <HelpSection :title="t('categoryManage.title')">
      <div class="flex items-center justify-between mb-4">
        <SectionHeader>{{ t('categoryManage.title') }}</SectionHeader>
        <PrimaryButton :icon="['fas', 'plus']">
          {{ t('categoryManage.create') }}
        </PrimaryButton>
      </div>

      <div class="space-y-2">
        <NeutralContainer class="flex items-center justify-between">
          <div>
            <span class="font-medium">{{ t('helpCenter.categories.dummyCatA') }}</span>
            <MutedText tag="span" size="sm" class="ml-2">
              ({{ t('categoryManage.maxShown', {count: 5}) }})
            </MutedText>
          </div>
          <div class="flex items-center gap-1">
            <IconButton :icon="['fas', 'chevron-up']" :label="t('common.moveUp')" disabled/>
            <IconButton :icon="['fas', 'chevron-down']" :label="t('common.moveDown')"/>
            <IconButton :icon="['fas', 'pen']" :label="t('common.edit')"/>
            <DeleteButton :label="t('common.delete')"/>
          </div>
        </NeutralContainer>
        <NeutralContainer class="flex items-center justify-between">
          <div>
            <span class="font-medium">{{ t('helpCenter.categories.dummyCatB') }}</span>
          </div>
          <div class="flex items-center gap-1">
            <IconButton :icon="['fas', 'chevron-up']" :label="t('common.moveUp')"/>
            <IconButton :icon="['fas', 'chevron-down']" :label="t('common.moveDown')"/>
            <IconButton :icon="['fas', 'pen']" :label="t('common.edit')"/>
            <DeleteButton :label="t('common.delete')"/>
          </div>
        </NeutralContainer>
        <NeutralContainer class="flex items-center justify-between">
          <div>
            <span class="font-medium">{{ t('helpCenter.categories.dummyCatC') }}</span>
          </div>
          <div class="flex items-center gap-1">
            <IconButton :icon="['fas', 'chevron-up']" :label="t('common.moveUp')"/>
            <IconButton :icon="['fas', 'chevron-down']" :label="t('common.moveDown')" disabled/>
            <IconButton :icon="['fas', 'pen']" :label="t('common.edit')"/>
            <DeleteButton :label="t('common.delete')"/>
          </div>
        </NeutralContainer>
      </div>
    </HelpSection>

    <!-- Dummy: Edit modal snapshot -->
    <HelpSection :title="t('helpCenter.categories.maxShownTitle')">
      <p>{{ t('helpCenter.categories.maxShownText') }}</p>
      <NeutralContainer class="space-y-4 mt-3">
        <SectionHeader>{{ t('categoryManage.edit') }}</SectionHeader>
        <div class="space-y-1">
          <FieldLabel>{{ t('categoryManage.name') }}</FieldLabel>
          <TextInput :model-value="t('helpCenter.categories.dummyCatA')" disabled/>
        </div>
        <div class="space-y-1">
          <FieldLabel>{{ t('categoryManage.maxShownLabel') }}</FieldLabel>
          <NumberInput :model-value="5" disabled/>
          <MutedText tag="p" size="sm">{{ t('categoryManage.maxShownHint') }}</MutedText>
        </div>
        <div class="flex items-center justify-between">
          <FieldLabel>{{ t('categoryManage.public') }}</FieldLabel>
          <ToggleInput :model-value="false"/>
        </div>
        <div class="flex justify-end gap-3">
          <SecondaryButton>{{ t('common.cancel') }}</SecondaryButton>
          <PrimaryButton>{{ t('common.save') }}</PrimaryButton>
        </div>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.categories.publicTitle')">
      <p>{{ t('helpCenter.categories.publicText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.categories.tip') }}</HelpTip>
  </HelpArticle>
</template>
