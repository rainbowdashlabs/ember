/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import ChipButton from '@/components/button/ChipButton.vue'
import type {ReactionGroupResponse} from '@/api/generated/schema'
import {personName} from '@/util/conversationTitle'

/**
 * The reactions under a message: each emoji with how many reacted with it. Clicking one adds or
 * takes back the reader's own; hovering, or resting a finger on it, names who reacted. The reader's
 * own are highlighted.
 */
defineProps<{
  reactions: readonly ReactionGroupResponse[]
}>()

defineEmits<{
  toggle: [emoji: string]
}>()

const {t} = useI18n()

function whoReacted(group: ReactionGroupResponse): string {
  return t('messages.reactedBy', {names: group.people.map(personName).join(', ')})
}
</script>

<template>
  <ul class="mt-1 flex flex-wrap gap-1">
    <li v-for="group in reactions" :key="group.emoji">
      <ChipButton :title="whoReacted(group)" :aria-pressed="group.mine" :aria-label="`${group.emoji} ${whoReacted(group)}`"
                  :class="group.mine ? 'border-primary bg-primary/15' : ''"
                  :data-testid="`reaction-${group.emoji}`" @click="$emit('toggle', group.emoji)">
        <span>{{ group.emoji }}</span>
        <span>{{ group.count }}</span>
      </ChipButton>
    </li>
  </ul>
</template>
