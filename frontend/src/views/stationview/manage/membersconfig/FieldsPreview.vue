/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import ProfileFieldsLayout from '@/components/profilefields/ProfileFieldsLayout.vue'
import type {ProfileField} from '@/api/profileFields'

/**
 * The fields as they will be filled in. Arranging them is guesswork while the only thing on the
 * page is the list that arranges them, so the arrangement is shown next to it.
 */
defineProps<{ fields: ProfileField[] }>()

const {t} = useI18n()

function nothing(): string {
  return ''
}
</script>

<template>
  <NeutralContainer class="space-y-3">
    <SubHeader class="text-sm">{{ t('membersConfig.previewTitle') }}</SubHeader>
    <MutedText tag="p" size="sm">{{ t('membersConfig.previewHint') }}</MutedText>
    <div class="pointer-events-none opacity-70">
      <ProfileFieldsLayout :fields="fields" :get-value="nothing" can-edit-readonly/>
    </div>
  </NeutralContainer>
</template>
