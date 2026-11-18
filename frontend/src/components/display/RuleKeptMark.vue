/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import AppIcon from '@/components/display/AppIcon.vue'

/** The small mark beside a group or tag that takes its members from a rule, naming that on hover. */
const {t} = useI18n()
</script>

<template>
  <AppIcon :icon="['fas', 'wand-magic-sparkles']" :title="t('memberRules.keptByRule')" class="text-xs"/>
</template>
