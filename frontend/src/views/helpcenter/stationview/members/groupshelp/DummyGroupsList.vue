/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedIconButton from '@/components/button/MutedIconButton.vue'
import ColorDot from '@/components/display/ColorDot.vue'

const {t} = useI18n()
</script>

<template>
  <div class="space-y-4">
    <div class="flex items-center justify-between">
      <SectionHeader>{{ t('memberGroups.title') }}</SectionHeader>
      <PrimaryButton :icon="['fas', 'plus']">
        {{ t('memberGroups.create') }}
      </PrimaryButton>
    </div>
    <div class="space-y-2">
      <NeutralContainer class="flex items-center justify-between gap-2 flex-wrap cursor-pointer border-primary">
        <span class="flex items-center gap-2">
          <ColorDot color="#3694FF"/>
          <span class="font-medium">Anfänger</span>
        </span>
        <div class="flex items-center gap-2">
          <MutedIconButton :icon="['fas', 'hashtag']" :label="t('memberGroups.convertToTag')"/>
          <EditButton/>
          <DeleteButton/>
        </div>
      </NeutralContainer>
      <NeutralContainer class="flex items-center justify-between gap-2 flex-wrap cursor-pointer hover:border-primary">
        <span class="flex items-center gap-2">
          <ColorDot color="#00C507"/>
          <span class="font-medium">Fortgeschrittene</span>
        </span>
        <div class="flex items-center gap-2">
          <MutedIconButton :icon="['fas', 'hashtag']" :label="t('memberGroups.convertToTag')"/>
          <EditButton/>
          <DeleteButton/>
        </div>
      </NeutralContainer>
    </div>
  </div>
</template>
