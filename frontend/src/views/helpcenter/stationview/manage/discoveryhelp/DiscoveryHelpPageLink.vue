/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import MutedText from '@/components/typography/MutedText.vue'
import AppIcon from '@/components/display/AppIcon.vue'

/** The "to the station" link of a sample tile, drawn without leading anywhere. */
const {t} = useI18n()
</script>

<template>
  <MutedText size="sm">
    <AppIcon :icon="['fas', 'globe']" aria-hidden="true" class="mr-1"/>
    {{ t('discovery.visitStation') }}
  </MutedText>
</template>
