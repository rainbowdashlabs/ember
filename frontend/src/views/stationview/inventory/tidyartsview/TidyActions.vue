/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'

/** What tidying the ticked names would do, and the two ways of doing it. */
defineProps<{
  /** How many pieces the ticked names stand for. */
  pieces: number
  /** The name the merge would write. */
  name: string
  canTidy: boolean
  merging: boolean
  assigning: boolean
}>()

const emit = defineEmits<{
  merge: []
  assign: []
}>()

const {t} = useI18n()
</script>

<template>
  <div class="space-y-2">
    <p class="text-sm">{{ t('inventory.art.willRename', {count: pieces, name}) }}</p>
    <ButtonRow>
      <PrimaryButton :disabled="!canTidy || merging" :icon="['fas', 'broom']"
                     data-testid="tidy-merge" @click="emit('merge')">
        {{ t('inventory.art.mergeSubmit') }}
      </PrimaryButton>
      <SecondaryButton :disabled="!canTidy || assigning" :icon="['fas', 'tags']"
                       data-testid="tidy-assign" @click="emit('assign')">
        {{ t('inventory.art.assignSubmit') }}
      </SecondaryButton>
    </ButtonRow>
    <p class="text-xs text-(--text-muted)">{{ t('inventory.art.assignHint') }}</p>
  </div>
</template>
