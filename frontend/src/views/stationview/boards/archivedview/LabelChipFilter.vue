/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import type {BoardLabel} from '@/api/boards'
import BoardLabelBadge from '../tickettable/BoardLabelBadge.vue'

/**
 * The labels of a board as chips, each pressed to narrow the list to what wears it. Several pressed
 * show what wears any of them.
 */
defineProps<{
  labels: BoardLabel[]
}>()

const selected = defineModel<Set<number>>({required: true})

const {t} = useI18n()

function toggle(id: number) {
  const next = new Set(selected.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  selected.value = next
}
</script>

<template>
  <div class="flex flex-wrap gap-1 items-center">
    <BoardLabelBadge
        v-for="label in labels"
        :key="label.id"
        :class="selected.has(label.id) ? 'ring-2 ring-offset-1 ring-[var(--text)]' : 'opacity-70 hover:opacity-100'"
        :label="label"
        class="cursor-pointer transition-all"
        @click="toggle(label.id)"
    />
    <IconButton
        v-if="selected.size > 0"
        :icon="['fas', 'xmark']"
        :label="t('boards.clearLabelFilter')"
        class="text-(--text-muted)"
        @click="selected = new Set()"
    />
  </div>
</template>
