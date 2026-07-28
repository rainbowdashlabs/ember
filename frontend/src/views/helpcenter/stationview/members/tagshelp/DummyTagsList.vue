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
      <SectionHeader>{{ t('userTags.title') }}</SectionHeader>
      <PrimaryButton :icon="['fas', 'plus']">
        {{ t('userTags.create') }}
      </PrimaryButton>
    </div>
    <div class="space-y-2">
      <NeutralContainer class="flex items-center justify-between gap-2 flex-wrap cursor-pointer border-primary">
        <span class="flex items-center gap-2">
          <ColorDot color="#ec2929"/>
          <span class="font-medium">Ersthelfer</span>
          <font-awesome-icon :icon="['fas', 'eye']" class="text-xs text-(--text-muted)"/>
        </span>
        <div class="flex items-center gap-2">
          <MutedIconButton :icon="['fas', 'people-group']" :label="t('userTags.convertToGroup')"/>
          <EditButton/>
          <DeleteButton/>
        </div>
      </NeutralContainer>
      <NeutralContainer class="flex items-center justify-between gap-2 flex-wrap cursor-pointer hover:border-primary">
        <span class="flex items-center gap-2">
          <ColorDot color="#3694FF"/>
          <span class="font-medium">Fahrer</span>
        </span>
        <div class="flex items-center gap-2">
          <MutedIconButton :icon="['fas', 'people-group']" :label="t('userTags.convertToGroup')"/>
          <EditButton/>
          <DeleteButton/>
        </div>
      </NeutralContainer>
    </div>
  </div>
</template>
