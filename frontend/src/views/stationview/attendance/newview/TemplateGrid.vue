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
import type {AttendanceTemplate} from '@/api/types'

const props = defineProps<{
  templates: AttendanceTemplate[]
}>()

const emit = defineEmits<{
  (e: 'select', templateId: number): void
}>()

const {t} = useI18n()
</script>

<template>
  <div class="space-y-3">
    <SubHeader>{{ t('attendanceNew.fromTemplate') }}</SubHeader>
    <MutedText tag="div" size="sm" class="py-2" v-if="props.templates.length === 0">
      {{ t('attendanceNew.noTemplates') }}
    </MutedText>
    <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
      <NeutralContainer v-for="tpl in props.templates" :key="tpl.id"
                        class="cursor-pointer hover:ring-2 hover:ring-primary/30 transition-all"
                        @click="emit('select', tpl.id)">
        <div class="flex items-center justify-between">
          <span class="font-medium">{{ tpl.name }}</span>
          <PrimaryButton :icon="['fas', 'plus']">
            {{ t('attendanceNew.create') }}
          </PrimaryButton>
        </div>
      </NeutralContainer>
    </div>
  </div>
</template>
