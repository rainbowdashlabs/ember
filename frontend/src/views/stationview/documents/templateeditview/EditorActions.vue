/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import type {DocumentTemplateResponse} from '@/api/generated/schema'

/**
 * Saving, duplicating, archiving and leaving the template, with the version it stands at.
 *
 * <p>A template is archived rather than deleted, because the documents generated from it keep naming
 * it; an archived one comes back with the same button. Duplicating copies the template as it was last
 * saved.
 */
defineProps<{
  saved: DocumentTemplateResponse | null
  /** Saves the template; the button shows its progress and rejects back to idle on a failure. */
  save: () => Promise<unknown>
  duplicating: boolean
  canSave: boolean
  /** The route of the list the template belongs to. */
  listRoute: string
}>()

const emit = defineEmits<{
  duplicate: []
  archive: [archived: boolean]
}>()

const {t} = useI18n()
const router = useRouter()
</script>

<template>
  <div class="flex flex-wrap items-center justify-between gap-3">
    <div class="flex flex-wrap items-center gap-2">
      <SecondaryBadge v-if="saved">{{ t('documentTemplates.version', {version: saved.version}) }}</SecondaryBadge>
      <SecondaryBadge v-if="saved?.archivedAt">{{ t('documentTemplates.isArchived') }}</SecondaryBadge>
    </div>
    <ButtonRow align="end">
      <SecondaryButton :icon="['fas', 'arrow-left']" @click="router.push({name: listRoute})">
        {{ t('documentTemplates.backToList') }}
      </SecondaryButton>
      <SecondaryButton v-if="saved" :icon="['fas', 'copy']" :disabled="duplicating" data-testid="template-duplicate"
                       @click="emit('duplicate')">
        {{ t('documentTemplates.duplicate') }}
      </SecondaryButton>
      <SecondaryButton v-if="saved" :icon="['fas', saved.archivedAt ? 'box-open' : 'box-archive']" data-testid="template-archive"
                       @click="emit('archive', !saved.archivedAt)">
        {{ saved.archivedAt ? t('documentTemplates.restore') : t('documentTemplates.archive') }}
      </SecondaryButton>
      <SaveButton :action="save" :disabled="!canSave" data-testid="template-save"/>
    </ButtonRow>
  </div>
</template>
