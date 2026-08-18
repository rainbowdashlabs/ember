/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'

const { t } = useI18n()

const splitChar = defineModel<string>('splitChar', {required: true})

defineProps<{
  csvColumn: string
  splitIndex: number
  isSplit: boolean
  isFirstSplitSibling: boolean
  previewText: string
}>()

defineEmits<{
  split: []
  addSplitPart: []
  unsplit: []
}>()
</script>

<template>
  <div>
    <div class="flex items-center gap-2">
      <span class="font-medium text-sm">
        {{ csvColumn }}
        <span v-if="isSplit" class="text-xs text-primary font-normal ml-1">
          ({{ t('memberImport.splitPart') }} {{ splitIndex + 1 }})
        </span>
      </span>
      <SecondaryButton v-if="!isSplit" :title="t('memberImport.splitAction')" class="!p-0.5 !min-w-0" @click="$emit('split')">
        <font-awesome-icon :icon="['fas', 'scissors']" class="h-3 w-3" />
      </SecondaryButton>
      <template v-else>
        <SecondaryButton :title="t('memberImport.addSplitPart')" class="!p-0.5 !min-w-0" @click="$emit('addSplitPart')">
          <font-awesome-icon :icon="['fas', 'plus']" class="h-3 w-3" />
        </SecondaryButton>
        <SecondaryButton :title="t('memberImport.unsplit')" class="!p-0.5 !min-w-0" @click="$emit('unsplit')">
          <font-awesome-icon :icon="['fas', 'xmark']" class="h-3 w-3" />
        </SecondaryButton>
      </template>
    </div>
    <div class="text-xs text-(--text-muted) truncate">{{ previewText || '-' }}</div>
    <div v-if="isSplit && isFirstSplitSibling" class="flex items-center gap-2 mt-1 text-xs">
      <label class="text-(--text-muted)">{{ t('memberImport.splitChar') }}:</label>
      <TextInput v-model="splitChar" class="w-12 text-center text-xs" />
    </div>
  </div>
</template>
