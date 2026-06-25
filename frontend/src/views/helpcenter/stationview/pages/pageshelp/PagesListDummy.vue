/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import PageRowDummy from './PageRowDummy.vue'

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-2">
    <div class="flex items-center justify-between flex-wrap gap-2 mb-3">
      <span class="font-semibold">{{ t('stationPages.title') }}</span>
      <PrimaryButton>
        <font-awesome-icon :icon="['fas', 'plus']" class="mr-1"/>
        {{ t('stationPages.createPage') }}
      </PrimaryButton>
    </div>
    <PageRowDummy
      :name="t('helpCenter.pages.dummyPage1')"
      :slug="t('helpCenter.pages.dummySlug1')"
      status="published"
      :toggle-icon="'eye-slash'"
      :toggle-label="t('stationPages.unpublish')"
      starred
    />
    <PageRowDummy
      :name="t('helpCenter.pages.dummyPage2')"
      :slug="t('helpCenter.pages.dummySlug2')"
      status="draft"
      :toggle-icon="'eye'"
      :toggle-label="t('stationPages.publish')"
      indented
    />
    <PageRowDummy
      :name="t('helpCenter.pages.dummyPage3')"
      :slug="t('helpCenter.pages.dummySlug3')"
      status="published"
      :toggle-icon="'eye-slash'"
      :toggle-label="t('stationPages.unpublish')"
      indented
    />
  </NeutralContainer>
</template>
