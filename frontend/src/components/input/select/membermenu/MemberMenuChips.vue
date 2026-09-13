/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import MemberName from '@/components/avatar/MemberName.vue'
import {identityOf, type MemberOption} from '../memberOption'

/**
 * Who a menu is already holding, where it holds several.
 *
 * <p>Chips rather than a count, because the usual selection is two or three people and reading them
 * should not cost a click. Past a handful they fold away: a group of fifty drawn in full turns the
 * field it sits in into a wall and pushes the rest of the form off the screen.
 */
const CHIP_FOLD = 5

const props = defineProps<{
  options: MemberOption[]
  disabled?: boolean
}>()

const emit = defineEmits<{
  remove: [option: MemberOption]
}>()

const {t} = useI18n()

const expanded = ref(false)

const visible = computed(() => (expanded.value ? props.options : props.options.slice(0, CHIP_FOLD)))

const foldedAway = computed(() => Math.max(0, props.options.length - visible.value.length))
</script>

<template>
  <div v-if="options.length > 0" class="flex flex-wrap items-center gap-1.5">
    <span
        v-for="option in visible"
        :key="option.value"
        data-testid="member-select-chip"
        class="inline-flex items-center gap-1 rounded-full bg-bg-light-accent px-2 py-1 dark:bg-bg-dark-accent"
    >
      <MemberName :identity="identityOf(option)" class="text-sm"/>
      <button
          type="button"
          :aria-label="t('memberSelect.remove', {name: option.name})"
          :disabled="disabled"
          class="text-(--text-muted) transition-colors hover:text-error disabled:opacity-50"
          @click.stop="emit('remove', option)"
      >
        <font-awesome-icon :icon="['fas', 'xmark']" class="h-3 w-3"/>
      </button>
    </span>
    <button
        v-if="foldedAway > 0 || expanded"
        type="button"
        data-testid="member-select-chip-fold"
        class="text-xs text-primary transition-colors hover:underline"
        @click.stop="expanded = !expanded"
    >
      {{ expanded ? t('memberSelect.showFewer') : t('memberSelect.showMore', {count: foldedAway}) }}
    </button>
  </div>
</template>
