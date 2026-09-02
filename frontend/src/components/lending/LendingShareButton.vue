/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import MutedIconButton from '@/components/button/MutedIconButton.vue'
import LendingShareModal from '@/components/lending/LendingShareModal.vue'
import {useLendingShare} from '@/composables/useLendingShare'
import type {ShareTarget} from '@/api/lending'

/**
 * The sharing control where there is room for one button and no room for a card, which is a row in
 * a list rather than a panel of its own.
 */
const props = withDefaults(defineProps<{
  target: ShareTarget
  targetId: number
  targetName: string
  /** Whether this gear is the station's to lend. Gear of the body above it is not. */
  lendable?: boolean
}>(), {lendable: true})

const emit = defineEmits<{ saved: [] }>()

const {t} = useI18n()
const {visible} = useLendingShare(() => props.lendable)

const editorOpen = ref(false)
</script>

<template>
  <MutedIconButton
      v-if="visible"
      :icon="['fas', 'share-nodes']"
      :label="t('lendingShare.edit')"
      data-testid="lending-share-button"
      @click="editorOpen = true"
  />
  <LendingShareModal
      v-if="visible"
      v-model="editorOpen"
      :target="target"
      :target-id="targetId"
      :target-name="targetName"
      @saved="emit('saved')"
  />
</template>
