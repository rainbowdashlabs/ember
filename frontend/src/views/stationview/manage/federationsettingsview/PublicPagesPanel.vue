/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'

const enabled = defineModel<boolean>('enabled', {required: true})

const props = defineProps<{
  publicUrl: string
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SubHeader>{{ t('stationManage.publicPages.title') }}</SubHeader>
    <MutedText size="sm">{{ t('stationManage.publicPages.hint') }}</MutedText>
    <div class="flex items-center justify-between">
      <span class="text-sm font-medium">{{ t('stationManage.publicPages.enabled') }}</span>
      <ToggleInput v-model="enabled"/>
    </div>
    <div v-if="enabled" class="space-y-1">
      <FieldLabel>{{ t('stationManage.publicPages.publicUrl') }}</FieldLabel>
      <code class="block rounded bg-bg-light-accent dark:bg-bg-dark-accent px-3 py-2 text-sm break-all select-all">
        {{ props.publicUrl }}
      </code>
    </div>
  </NeutralContainer>
</template>
