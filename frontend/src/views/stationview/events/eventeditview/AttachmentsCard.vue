/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import IconButton from '@/components/button/IconButton.vue'
import DragList from '@/components/input/DragList.vue'
import MediaBrowseButton from '@/components/media/MediaBrowseButton.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import {formatSize} from '@/util/format'
import type {StationFile} from '@/api/media'
import type {EventAttachment} from '@/api/events'
import type {Failure} from '@/util/failure'

const attachments = defineModel<EventAttachment[]>('attachments', {required: true})

defineProps<{
  stationUid: string
  /** What went wrong the last time something was written, or null while everything has landed. */
  failure?: Failure | null
}>()

const emit = defineEmits<{
  (e: 'add', file: StationFile): void
  (e: 'save', attachment: EventAttachment): void
  (e: 'remove', index: number): void
  (e: 'reorder', fromIndex: number, toIndex: number): void
}>()

const {t} = useI18n()

/**
 * Writes the switch and only then hands the row on to be saved.
 *
 * <p>Two-way binding and a save listener on the same event are run in an order nothing here
 * decides, and saving first sends the value the switch had a moment ago: the file stayed open for
 * everyone while the screen showed it as kept back.
 */
function keepBack(attachment: EventAttachment, internal: boolean) {
  attachment.internal = internal
  emit('save', attachment)
}
</script>

<template>
  <NeutralContainer class="space-y-3" data-testid="event-attachments">
    <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
      <div>
        <SubHeader>{{ t('events.attachments.title') }}</SubHeader>
        <p class="text-xs text-(--text-muted)">{{ t('events.attachments.hint') }}</p>
      </div>
      <MediaBrowseButton
          :label="t('events.attachments.add')"
          :station-uid="stationUid"
          @pick="p => emit('add', p.file)"
      />
    </div>

    <p class="text-xs text-(--text-muted)">{{ t('events.attachments.internalHint') }}</p>

    <FailureAlert :failure="failure" data-testid="event-attachment-failure"/>

    <p v-if="attachments.length === 0" class="text-sm text-(--text-muted)">
      {{ t('events.attachments.empty') }}
    </p>

    <DragList
        v-else
        :items="attachments"
        :key-fn="(attachment) => attachment.id"
        class="space-y-2"
        @reorder="(from, to) => emit('reorder', from, to)"
    >
      <template #default="{item: attachment, index}">
        <div
            :data-attachment="attachment.id"
            class="flex flex-col sm:flex-row sm:items-center gap-2 rounded-lg border border-(--border) p-2"
            data-testid="event-attachment"
        >
          <div class="flex-1 min-w-0">
            <p class="text-sm truncate">{{ attachment.fileName }}</p>
            <p class="text-xs text-(--text-muted)">{{ formatSize(attachment.fileSize) }}</p>
          </div>
          <TextInput
              :model-value="attachment.label ?? ''"
              :placeholder="t('events.attachments.labelPlaceholder')"
              class="sm:w-56 !text-sm"
              @blur="emit('save', attachment)"
              @update:model-value="value => attachment.label = value ?? null"
          />
          <label class="flex items-center gap-2">
            <ToggleInput
                :model-value="attachment.internal"
                data-testid="event-attachment-internal"
                @update:model-value="value => keepBack(attachment, value)"
            />
            <FieldLabel inline>{{ t('events.attachments.internal') }}</FieldLabel>
          </label>
          <IconButton
              :icon="['fas', 'trash']"
              :label="t('events.attachments.remove')"
              data-testid="event-attachment-remove"
              @click="emit('remove', index)"
          />
        </div>
      </template>
    </DragList>
  </NeutralContainer>
</template>
