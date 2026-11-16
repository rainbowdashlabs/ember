/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import FileThumbnail from '@/components/documents/FileThumbnail.vue'
import IconButton from '@/components/button/IconButton.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import {stationTemplateSource} from '@/api/documentTemplates'
import type {RequiredTemplate} from '@/api/generated/schema'

/**
 * One document an appointment asks for: a small picture of it, its name, and a button taking it off. One
 * the event group of a part asks for is marked as the event group's and cannot be taken off here.
 */
defineProps<{
  template: RequiredTemplate
}>()

const emit = defineEmits<{
  remove: []
}>()

const ROW_PICTURE_SIZE = 128

const {t} = useI18n()
</script>

<template>
  <li class="flex items-center gap-2" data-testid="document-requirement">
    <FileThumbnail :url="stationTemplateSource.pictureUrl(template.templateId, ROW_PICTURE_SIZE)"
                   mime-type="application/pdf" :alt="template.name" anchor-top/>
    <span class="flex-1">{{ template.name }}</span>
    <SecondaryBadge v-if="template.archived">{{ t('events.documents.archived') }}</SecondaryBadge>
    <SecondaryBadge v-if="template.fromEventGroup" data-testid="document-requirement-from-group">
      {{ t('eventGroups.fromEventGroup') }}
    </SecondaryBadge>
    <IconButton v-else :icon="['fas', 'xmark']" :label="t('events.documents.remove')" @click="emit('remove')"/>
  </li>
</template>
