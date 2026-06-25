/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'

const enabled = defineModel<boolean>('enabled', {required: true})

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SubHeader>{{ t('helpCenter.federation.publicBlogTitle') }}</SubHeader>
    <MutedText size="sm">{{ t('helpCenter.federation.publicBlogText') }}</MutedText>
    <div class="flex items-center justify-between">
      <span class="text-sm font-medium">{{ t('helpCenter.federation.publicBlogTitle') }}</span>
      <ToggleInput v-model="enabled"/>
    </div>
  </NeutralContainer>
</template>
