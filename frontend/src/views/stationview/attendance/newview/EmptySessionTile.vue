/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'

/**
 * The way in for a sheet whose people no template describes.
 *
 * <p>Until this stood here, a station that wanted one kept a template with no groups on it, which
 * then sat in the template list, the report and the configuration screen saying nothing to anybody
 * who found it.
 */
const emit = defineEmits<{
  (e: 'start'): void
}>()

const {t} = useI18n()
</script>

<template>
  <div class="space-y-3">
    <SubHeader>{{ t('attendanceNew.withoutTemplate') }}</SubHeader>
    <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
      <NeutralContainer
          class="cursor-pointer hover:ring-2 hover:ring-primary/30 transition-all"
          data-testid="attendance-start-empty"
          @click="emit('start')"
      >
        <div class="space-y-2">
          <div class="flex items-center justify-between gap-2">
            <span class="font-medium">{{ t('attendanceNew.emptyTitle') }}</span>
            <PrimaryButton :icon="['fas', 'plus']">{{ t('attendanceNew.create') }}</PrimaryButton>
          </div>
          <MutedText tag="p" size="sm">{{ t('attendanceNew.emptyHint') }}</MutedText>
        </div>
      </NeutralContainer>
    </div>
  </div>
</template>
