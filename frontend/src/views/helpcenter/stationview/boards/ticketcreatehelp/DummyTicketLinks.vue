/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import SubHeader from '@/components/typography/SubHeader.vue'

const {t} = useI18n()
</script>

<template>
  <NeutralContainer>
    <SubHeader class="mb-2">{{ t('boards.links') }}</SubHeader>
    <div class="flex items-center gap-2 py-0.5">
      <font-awesome-icon :icon="['fas', 'link']" class="text-(--text-muted) text-xs shrink-0" />
      <span class="text-xs text-(--text-muted)">{{ t('boards.linkBlockedBy') }}</span>
      <span class="text-sm truncate flex-1">JF-12 {{ t('helpCenter.ticketCreate.linkedTicketExample') }}</span>
      <IconButton :icon="['fas', 'xmark']" label="Remove" class="text-xs" />
    </div>
    <div class="flex gap-2 mt-2 items-center flex-wrap">
      <SelectInput model-value="RELATES_TO" class="w-40">
        <option value="RELATES_TO">{{ t('boards.linkRelatesTo') }}</option>
        <option value="BLOCKS">{{ t('boards.linkBlocks') }}</option>
        <option value="BLOCKED_BY">{{ t('boards.linkBlockedBy') }}</option>
        <option value="CAUSES">{{ t('boards.linkCauses') }}</option>
        <option value="CAUSED_BY">{{ t('boards.linkCausedBy') }}</option>
      </SelectInput>
      <SelectInput model-value="" class="flex-1 min-w-0">
        <option value="">{{ t('boards.linkedTickets') }}...</option>
        <option value="1">JF-10 Schläuche bestellen</option>
        <option value="2">JF-11 Helme sortieren</option>
      </SelectInput>
      <IconButton :icon="['fas', 'plus']" :label="t('common.add')" class="text-(--text-muted)" />
    </div>
  </NeutralContainer>
</template>
