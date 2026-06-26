/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'

defineProps<{
  title: string
  allItems: boolean
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3 mb-3">
    <div class="flex items-center justify-between mb-2">
      <SubHeader>{{ title }}</SubHeader>
      <DeleteButton />
    </div>
    <FieldLabel inline class="cursor-pointer">
      <ToggleInput :model-value="allItems" disabled />
      {{ t('lending.blockItemAll') }}
    </FieldLabel>
    <slot/>
  </NeutralContainer>
</template>
