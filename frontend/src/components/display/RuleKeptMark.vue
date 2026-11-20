/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import AppIcon from '@/components/display/AppIcon.vue'

/**
 * The small mark beside a group or tag that takes its members from a rule. It names that on hover and to a screen
 * reader; where somebody asks why a membership cannot be changed by hand, as on a member's chips, it also says so
 * in a word, since a phone shows no hover.
 */
defineProps<{
  /** Whether the mark says in a word what it means, beside the icon. */
  labelled?: boolean
}>()

const {t} = useI18n()
</script>

<template>
  <span class="inline-flex items-center gap-1" :title="t('memberRules.keptByRule')">
    <AppIcon :icon="['fas', 'wand-magic-sparkles']" class="text-xs" aria-hidden="true"/>
    <span v-if="labelled" class="text-xs">{{ t('memberRules.byRuleShort') }}</span>
    <span v-else class="sr-only">{{ t('memberRules.keptByRule') }}</span>
  </span>
</template>
