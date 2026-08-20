/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import MarkdownEditor from '@/components/input/MarkdownEditor.vue'
import ContentBlockEditor from '@/components/content/ContentBlockEditor.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import {StationUserType, StationUserTypeLabels, type StationUserTypeName} from '@/api/types'
import {ContentMode, type ContentModeName} from '@/api/news'
import {INSTANCE_MEDIA_SCOPE} from '@/api/media'
import {markdownAsSingleBlock} from '@/util/blockSwitch'
import type {SystemNewsEntry} from '@/api/adminNews'
import type {RowEditData} from '@/components/content/blockeditor/EditorRow.vue'

const props = defineProps<{
  /** The entry being corrected, or null while a new one is being written. */
  entry: SystemNewsEntry | null
  saving: boolean
}>()

const emit = defineEmits<{
  (e: 'save', payload: {
    title: string
    contentMarkdown: string
    userTypes: string[]
    notifyMembers: boolean
    contentMode: ContentModeName
    rows: RowEditData[]
  }): void
  (e: 'cancel'): void
}>()

const {t} = useI18n()

const title = ref('')
const contentMarkdown = ref('')
const userTypes = ref<string[]>([])
const notifyMembers = ref(false)
const contentMode = ref<ContentModeName>(ContentMode.SIMPLE)
const rows = ref<RowEditData[]>([])

watch(() => props.entry, entry => {
  title.value = entry?.title ?? ''
  contentMarkdown.value = entry?.contentMarkdown ?? ''
  userTypes.value = [...(entry?.userTypes ?? [])]
  contentMode.value = entry?.contentMode ?? ContentMode.SIMPLE
  rows.value = (entry?.rows ?? []).map(row => ({
    id: row.id,
    sortOrder: row.sortOrder,
    cells: row.cells.map(cell => ({
      id: cell.id,
      sortOrder: cell.sortOrder,
      widthPercent: cell.widthPercent,
      contentType: cell.contentType,
      content: cell.content,
      config: cell.config as Record<string, unknown>,
    })),
  }))
  // Correcting an entry does not notify again: people were told when it went out, and telling
  // them a second time because a typo was fixed is what makes a notification worth ignoring.
  notifyMembers.value = false
}, {immediate: true})

const allUserTypes = Object.values(StationUserType)

const canSave = computed(() =>
    !!title.value.trim() && (contentMode.value === ContentMode.RICH || !!contentMarkdown.value.trim()),
)

const isEdit = computed(() => props.entry !== null)

function toggleUserType(userType: string) {
  userTypes.value = userTypes.value.includes(userType)
      ? userTypes.value.filter(u => u !== userType)
      : [...userTypes.value, userType]
}

/**
 * Switches to the page editor, carrying what is already written into a first block.
 *
 * <p>An entry that exists is switched by the server, which is what records the change. One that
 * does not exist yet is switched here, into the shape the server would have produced, and told to
 * the server as soon as saving gives it an id.
 */
function enableBlocks() {
  if (contentMode.value === ContentMode.RICH) return
  rows.value = markdownAsSingleBlock(contentMarkdown.value)
  contentMode.value = ContentMode.RICH
}

function submit() {
  if (!canSave.value) return
  emit('save', {
    title: title.value,
    contentMarkdown: contentMarkdown.value,
    userTypes: userTypes.value,
    notifyMembers: notifyMembers.value,
    contentMode: contentMode.value,
    rows: rows.value,
  })
}

function label(userType: string): string {
  return StationUserTypeLabels[userType as StationUserTypeName] ?? userType
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SubHeader>{{ isEdit ? t('adminNews.editTitle') : t('adminNews.createTitle') }}</SubHeader>

    <div class="space-y-1">
      <FieldLabel>{{ t('adminNews.entryTitle') }}</FieldLabel>
      <TextInput v-model="title" :placeholder="t('adminNews.entryTitlePlaceholder')"/>
    </div>

    <div class="space-y-1">
      <FieldLabel>{{ t('adminNews.content') }}</FieldLabel>
      <template v-if="contentMode === ContentMode.RICH">
        <!-- The instance's own library: a system notice is read in every station, so its pictures
             cannot come out of one of them. -->
        <ContentBlockEditor v-model:rows="rows" :station-uid="INSTANCE_MEDIA_SCOPE"/>
      </template>
      <template v-else>
        <MarkdownEditor
            v-model="contentMarkdown"
            :media-scope="INSTANCE_MEDIA_SCOPE"
            :placeholder="t('adminNews.contentPlaceholder')"
        />
        <div class="flex flex-col gap-2 pt-2 sm:flex-row sm:items-center">
          <SecondaryButton :icon="['fas', 'table-columns']" @click="enableBlocks">
            {{ t('adminNews.enableBlocks') }}
          </SecondaryButton>
          <p class="text-xs text-(--text-muted)">{{ t('adminNews.enableBlocksHint') }}</p>
        </div>
      </template>
    </div>

    <div class="space-y-1">
      <FieldLabel>{{ t('adminNews.audience') }}</FieldLabel>
      <p class="text-xs text-(--text-muted)">{{ t('adminNews.audienceHint') }}</p>
      <div class="flex flex-wrap gap-2 pt-1">
        <SecondaryButton
            v-for="userType in allUserTypes"
            :key="userType"
            :class="userTypes.includes(userType) ? 'ring-2 ring-primary' : ''"
            @click="toggleUserType(userType)"
        >
          {{ label(userType) }}
        </SecondaryButton>
      </div>
    </div>

    <div class="space-y-1">
      <FieldLabel>{{ t('adminNews.notify') }}</FieldLabel>
      <div class="flex items-center gap-2">
        <ToggleInput v-model="notifyMembers" :disabled="isEdit"/>
        <span class="text-xs text-(--text-muted)">
          {{ isEdit ? t('adminNews.notifyEditHint') : t('adminNews.notifyHint') }}
        </span>
      </div>
    </div>

    <div class="flex justify-end gap-3">
      <SecondaryButton @click="emit('cancel')">{{ t('common.cancel') }}</SecondaryButton>
      <PrimaryButton :disabled="!canSave || saving" @click="submit">
        {{ isEdit ? t('common.save') : t('adminNews.publish') }}
      </PrimaryButton>
    </div>
  </NeutralContainer>
</template>
