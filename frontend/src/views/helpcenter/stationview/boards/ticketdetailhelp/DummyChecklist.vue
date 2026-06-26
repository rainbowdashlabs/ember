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

const items = [
  {done: true, key: 'checkItem1'},
  {done: false, key: 'checkItem2'},
  {done: false, key: 'checkItem3'},
]
</script>

<template>
  <NeutralContainer>
    <div class="flex items-center justify-between mb-2">
      <SubHeader>{{ t('boards.checklist') }} (1/3)</SubHeader>
      <IconButton :icon="['fas', 'trash']" :label="t('common.delete')" class="text-xs text-(--text-muted)"/>
    </div>
    <div class="h-1.5 bg-(--bg-muted) rounded-full mb-3 overflow-hidden">
      <div class="h-full rounded-full bg-primary" style="width: 33%"/>
    </div>
    <div v-for="item in items" :key="item.key" class="flex items-center gap-2 py-0.5">
      <font-awesome-icon :icon="['fas', 'grip-vertical']" class="text-(--text-muted) cursor-grab text-xs"/>
      <CheckboxInput :model-value="item.done"/>
      <span class="flex-1 text-sm" :class="item.done ? 'line-through text-(--text-muted)' : ''">
        {{ t(`helpCenter.ticketDetail.${item.key}`) }}
      </span>
      <IconButton :icon="['fas', 'xmark']" label="Remove" class="text-xs"/>
    </div>
    <div class="flex gap-2 mt-2 items-center">
      <TextInput model-value="" :placeholder="t('boards.addChecklistItem')" class="flex-1 text-sm"/>
      <IconButton :icon="['fas', 'plus']" :label="t('common.add')" class="text-(--text-muted)"/>
    </div>
  </NeutralContainer>
</template>
