/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SubHeader from '@/components/typography/SubHeader.vue'

const {t} = useI18n()
</script>

<template>
  <NeutralContainer>
    <SubHeader class="mb-2">{{ t('boards.checklist') }}</SubHeader>
    <div class="flex items-center gap-2 py-0.5">
      <CheckboxInput :model-value="true" />
      <span class="flex-1 text-sm line-through text-(--text-muted)">{{ t('helpCenter.ticketCreate.checkItem1') }}</span>
      <IconButton :icon="['fas', 'xmark']" label="Remove" class="text-xs" />
    </div>
    <div class="flex items-center gap-2 py-0.5">
      <CheckboxInput :model-value="false" />
      <span class="flex-1 text-sm">{{ t('helpCenter.ticketCreate.checkItem2') }}</span>
      <IconButton :icon="['fas', 'xmark']" label="Remove" class="text-xs" />
    </div>
    <div class="flex gap-2 mt-2 items-center">
      <TextInput model-value="" :placeholder="t('boards.addChecklistItem')" class="flex-1 text-sm" />
      <IconButton :icon="['fas', 'plus']" :label="t('common.add')" class="text-(--text-muted)" />
    </div>
  </NeutralContainer>
</template>
