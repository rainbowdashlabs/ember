/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import MemberName from '@/components/avatar/MemberName.vue'
import MutedText from '@/components/typography/MutedText.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import DropdownOption from '../dropdown/DropdownOption.vue'
import {identityOf, type MemberOption} from '../memberOption'

/**
 * One row of a member menu: a person, or the empty answer.
 *
 * <p>The empty answer is drawn here rather than beside the list so the keyboard reaches it without
 * knowing it is special: it is row zero like any other, its value is the empty one, and walking
 * onto it and pressing Enter is how a ticket is unassigned.
 */
defineProps<{
  /** The person this row offers, or {@code null} where the row is the empty answer. */
  option: MemberOption | null
  /** Whether the menu takes several, which is what puts a tick at the front of the row. */
  multiple?: boolean
  chosen?: boolean
  /** What the empty answer is called, where "nobody" is not what it means. */
  emptyLabel?: string
}>()

const {t} = useI18n()
</script>

<template>
  <DropdownOption
      :value="option?.value ?? ''"
      :data-testid="option ? 'member-select-option' : 'member-select-empty'"
  >
    <font-awesome-icon
        v-if="multiple"
        :icon="['fas', chosen ? 'square-check' : 'square']"
        :class="chosen ? 'text-primary' : 'text-(--text-muted) opacity-40'"
        class="h-4 w-4 shrink-0"
    />
    <template v-if="option">
      <MemberName :identity="identityOf(option)" class="min-w-0"/>
      <InfoBadge v-if="option.note" class="shrink-0">{{ option.note }}</InfoBadge>
    </template>
    <template v-else>
      <font-awesome-icon :icon="['fas', 'user-slash']" class="h-4 w-4 shrink-0 text-(--text-muted)"/>
      <MutedText size="sm">{{ emptyLabel ?? t('memberSelect.nobody') }}</MutedText>
    </template>
  </DropdownOption>
</template>
