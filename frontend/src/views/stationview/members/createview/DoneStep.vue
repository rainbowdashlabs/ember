/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'

const {t} = useI18n()

const emit = defineEmits<{
  createAnother: []
  toList: []
}>()
</script>

<template>
  <NeutralContainer class="space-y-4 text-center py-8">
    <font-awesome-icon :icon="['fas', 'circle-check']" class="text-4xl text-success"/>
    <SectionHeader>{{ t('membersCreate.done') }}</SectionHeader>
    <p class="text-sm text-(--text-muted)">{{ t('membersCreate.doneHint') }}</p>
    <div class="flex justify-center gap-3">
      <SecondaryButton @click="emit('createAnother')">{{ t('membersCreate.createAnother') }}</SecondaryButton>
      <PrimaryButton @click="emit('toList')">{{ t('membersCreate.toList') }}</PrimaryButton>
    </div>
  </NeutralContainer>
</template>
