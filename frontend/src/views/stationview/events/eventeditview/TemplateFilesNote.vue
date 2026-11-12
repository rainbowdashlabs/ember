/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import type {EventAttachment} from '@/api/generated/schema'

/**
 * Where the files card stands while an appointment is created from a template that carries files.
 *
 * <p>The appointment does not exist yet, so nothing can be attached to it here. The server attaches
 * the template's files when it creates the appointment, and this names them so nobody attaches them
 * a second time afterwards.
 */
defineProps<{
  files: EventAttachment[]
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-2" data-testid="template-files-note">
    <SubHeader>{{ t('events.attachments.title') }}</SubHeader>
    <p class="text-xs text-(--text-muted)">{{ t('events.attachments.fromTemplate') }}</p>
    <ul class="list-disc pl-5 text-sm">
      <li v-for="file in files" :key="file.id">{{ file.label || file.fileName }}</li>
    </ul>
  </NeutralContainer>
</template>
