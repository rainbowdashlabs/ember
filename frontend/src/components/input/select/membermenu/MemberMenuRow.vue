/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import MemberName from '@/components/avatar/MemberName.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {identityOf, type MemberOption} from '../memberOption'

/**
 * One row of a member menu: a person, or the empty answer.
 *
 * <p>The empty answer is drawn here rather than beside the list so the keyboard reaches it without
 * knowing it is special: it is row zero like any other, and walking onto it and pressing Enter is how
 * a ticket is unassigned.
 */
defineProps<{
  /** The person this row offers, or {@code null} where the row is the empty answer. */
  option: MemberOption | null
  highlighted: boolean
  /** Whether the menu takes several, which is what puts a tick at the front of the row. */
  multiple?: boolean
  chosen?: boolean
  /** What the empty answer is called, where "nobody" is not what it means. */
  emptyLabel?: string
}>()

const emit = defineEmits<{
  take: []
  hover: []
}>()

const {t} = useI18n()
</script>

<template>
  <button
      data-row
      type="button"
      :data-testid="option ? 'member-select-option' : 'member-select-empty'"
      :class="highlighted ? 'bg-primary/10' : 'hover:bg-primary/5'"
      class="flex w-full items-center gap-2 px-3 py-2 text-left text-sm transition-colors"
      @click="emit('take')"
      @mouseenter="emit('hover')"
  >
    <font-awesome-icon
        v-if="multiple"
        :icon="['fas', chosen ? 'square-check' : 'square']"
        :class="chosen ? 'text-primary' : 'text-(--text-muted) opacity-40'"
        class="h-4 w-4 shrink-0"
    />
    <template v-if="option">
      <MemberName :identity="identityOf(option)" class="min-w-0"/>
      <MutedText v-if="option.email" class="truncate">{{ option.email }}</MutedText>
    </template>
    <template v-else>
      <font-awesome-icon :icon="['fas', 'user-slash']" class="h-4 w-4 shrink-0 text-(--text-muted)"/>
      <MutedText size="sm">{{ emptyLabel ?? t('memberSelect.nobody') }}</MutedText>
    </template>
  </button>
</template>
