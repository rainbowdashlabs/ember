/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import Modal from '@/components/feedback/Modal.vue'
import Alert from '@/components/feedback/Alert.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import AsyncSection from '@/components/feedback/AsyncSection.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import type {CalendarFeedResponse, EventCategory, StationFeedLevel} from '@/api/generated/schema'
import {calendarFeeds, events} from '@/api'
import {useSession} from '@/composables/useSession'
import {useConfirmDelete} from '@/composables/useConfirmDelete'
import {useConfirmAction} from '@/composables/useConfirmAction'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useFlashMessage} from '@/composables/useFlashMessage'
import {describeFailure, type Failure} from '@/util/failure'
import {formatDateTime} from '@/util/format'
import FeedCategoryChoice from '@/components/input/FeedCategoryChoice.vue'

const {t} = useI18n()
const {loaded} = useSession()

const LEVELS: StationFeedLevel[] = ['PUBLIC', 'MEMBERS', 'ALL']

const feeds = ref<CalendarFeedResponse[]>([])
const categories = ref<EventCategory[]>([])

const editOpen = ref(false)
const editId = ref<number | null>(null)
const editName = ref('')
const editLevel = ref<StationFeedLevel>('MEMBERS')
const editHidden = ref<number[]>([])
const editLevelBefore = ref<StationFeedLevel | null>(null)

const {loading, failure, reload} = useAsyncLoader(async () => {
  const [loadedFeeds, loadedCategories] = await Promise.all([calendarFeeds.listCalendarFeeds(), events.listCategories()])
  feeds.value = loadedFeeds
  categories.value = loadedCategories
}, {autoLoad: loaded.value})

watch(loaded, v => { if (v) reload() })

const categoryNames = computed(() => new Map(categories.value.map(category => [category.id, category.name])))

const {message: copied, flash} = useFlashMessage(2000)

const {
  show: deleteOpen,
  target: deleteTarget,
  requestDelete,
  confirm: confirmDelete,
} = useConfirmDelete<CalendarFeedResponse>({
  onDelete: feed => calendarFeeds.deleteCalendarFeed(feed.id),
  onSuccess: () => reload(),
  failure,
})

const {show: regenerateOpen, request: requestRegenerate, confirm: confirmRegenerate} =
    useConfirmAction<CalendarFeedResponse>({
      onConfirm: async feed => {
        try {
          await calendarFeeds.regenerateCalendarFeed(feed.id)
        } catch (e) {
          failure.value = describeFailure(e, t)
          return
        }
        await reload()
      },
    })

const everythingOpen = ref(false)
const editFailure = ref<Failure | null>(null)

/**
 * The question before reaching restricted appointments is always asked, a held shift key included:
 * it is the one thing standing between a feed address and the station's restricted appointments.
 */
async function confirmEverything() {
  everythingOpen.value = false
  await store().catch(() => undefined)
}

function openCreate() {
  editId.value = null
  editName.value = ''
  editLevel.value = 'MEMBERS'
  editHidden.value = []
  editLevelBefore.value = null
  editFailure.value = null
  editOpen.value = true
}

function openEdit(feed: CalendarFeedResponse) {
  editId.value = feed.id
  editName.value = feed.name
  editLevel.value = feed.level
  editHidden.value = [...feed.hiddenCategories]
  editLevelBefore.value = feed.level
  editFailure.value = null
  editOpen.value = true
}

/**
 * A feed that reaches restricted appointments for the first time asks first: everybody holding its
 * address sees them.
 */
async function save() {
  if (editLevel.value === 'ALL' && editLevelBefore.value !== 'ALL') {
    everythingOpen.value = true
    return
  }
  await store()
}

/**
 * Storing the feed and reading the list back are answered for separately, so a feed that was saved
 * and a list that then failed to refresh does not read as a feed that was refused. A refused save
 * shows in the dialog, which stays open, rather than behind it on the page.
 */
async function store() {
  const body = {name: editName.value, level: editLevel.value, hiddenCategories: editHidden.value}
  try {
    if (editId.value) {
      await calendarFeeds.updateCalendarFeed(editId.value, body)
    } else {
      await calendarFeeds.createCalendarFeed(body)
    }
    editOpen.value = false
  } catch (e) {
    editFailure.value = describeFailure(e, t)
    throw e
  }
  await reload()
}

async function copyAddress(feed: CalendarFeedResponse) {
  const url = calendarFeeds.stationFeedUrl(feed.token)
  await navigator.clipboard.writeText(url)
  flash(String(feed.id))
}

function hiddenNames(feed: CalendarFeedResponse): string {
  return feed.hiddenCategories.map(id => categoryNames.value.get(id)).filter(Boolean).join(', ')
}
</script>

<template>
  <ViewContent
      :title="t('pages.event-calendar-feeds.title')"
      :subtitle="t('pages.event-calendar-feeds.subtitle')"
  >
    <div class="flex items-center justify-between gap-4 mb-4">
      <MutedText tag="p" size="sm">{{ t('calendarFeeds.intro') }}</MutedText>
      <PrimaryButton :icon="['fas', 'plus']" data-testid="calendar-feed-create" @click="openCreate">
        {{ t('calendarFeeds.create') }}
      </PrimaryButton>
    </div>

    <AsyncSection
        :empty="feeds.length === 0"
        :empty-compact="true"
        :empty-message="t('calendarFeeds.empty')"
        :failure="failure"
        :loading="loading"
    >
      <div class="space-y-2">
        <NeutralContainer
            v-for="feed in feeds"
            :key="feed.id"
            class="space-y-2"
            :data-testid="`calendar-feed-${feed.id}`"
        >
          <div class="flex items-center justify-between gap-2">
            <div class="min-w-0">
              <div class="font-medium">{{ feed.name }}</div>
              <MutedText tag="div" size="sm">{{ t('calendarFeeds.level.' + feed.level) }}</MutedText>
              <MutedText v-if="feed.hiddenCategories.length > 0" tag="div" size="sm">
                {{ t('calendarFeeds.leavesOut', {names: hiddenNames(feed)}) }}
              </MutedText>
              <MutedText tag="div" size="sm">
                {{ feed.polledAt ? t('calendarFeeds.lastFetched', {when: formatDateTime(feed.polledAt)}) : t('calendarFeeds.neverFetched') }}
              </MutedText>
            </div>
            <div class="flex items-center gap-1 shrink-0">
              <IconButton
                  :icon="['fas', copied === String(feed.id) ? 'check' : 'copy']"
                  :label="t('calendarFeeds.copy')"
                  @click="copyAddress(feed)"
              />
              <IconButton :icon="['fas', 'rotate']" :label="t('calendarFeeds.regenerate')" @click="requestRegenerate(feed)"/>
              <IconButton :icon="['fas', 'pen']" :label="t('common.edit')" @click="openEdit(feed)"/>
              <DeleteButton :label="t('common.delete')" @click="requestDelete(feed)"/>
            </div>
          </div>
        </NeutralContainer>
      </div>
    </AsyncSection>

    <Modal v-model="editOpen">
      <div class="space-y-4">
        <SectionHeader>{{ editId ? t('calendarFeeds.edit') : t('calendarFeeds.create') }}</SectionHeader>
        <div class="space-y-1">
          <FieldLabel>{{ t('calendarFeeds.name') }}</FieldLabel>
          <TextInput v-model="editName" :placeholder="t('calendarFeeds.namePlaceholder')" :maxlength="100"/>
        </div>
        <div class="space-y-1">
          <FieldLabel>{{ t('calendarFeeds.levelLabel') }}</FieldLabel>
          <div class="flex flex-wrap gap-2">
            <SelectionToggleButton
                v-for="level in LEVELS"
                :key="level"
                :selected="editLevel === level"
                size="md"
                :data-testid="`calendar-feed-level-${level}`"
                @toggle="editLevel = level"
            >{{ t('calendarFeeds.level.' + level) }}</SelectionToggleButton>
          </div>
          <MutedText tag="p" size="sm">{{ t('calendarFeeds.levelHint.' + editLevel) }}</MutedText>
          <Alert v-if="editLevel === 'ALL'" variant="info">{{ t('calendarFeeds.everythingWarning') }}</Alert>
        </div>
        <FeedCategoryChoice v-model="editHidden" :categories="categories"/>
        <FailureAlert :failure="editFailure"/>

        <div class="flex justify-end gap-3">
          <SecondaryButton @click="editOpen = false">{{ t('common.cancel') }}</SecondaryButton>
          <SaveButton :disabled="!editName.trim()" :action="save" data-testid="calendar-feed-save"/>
        </div>
      </div>
    </Modal>

    <ConfirmDeleteModal
        v-model="everythingOpen"
        :title="t('calendarFeeds.everythingConfirmTitle')"
        :message="t('calendarFeeds.everythingWarning')"
        :confirm-label="t('calendarFeeds.everythingConfirm')"
        confirm-test-id="calendar-feed-everything-confirm"
        data-confirm-always
        neutral
        @confirm="confirmEverything"
    />

    <ConfirmDeleteModal
        v-model="regenerateOpen"
        :title="t('calendarFeeds.regenerateConfirmTitle')"
        :message="t('calendarFeeds.regenerateConfirmBody')"
        :confirm-label="t('calendarFeeds.regenerate')"
        :confirm-icon="['fas', 'rotate']"
        neutral
        @confirm="confirmRegenerate"
    />

    <ConfirmDeleteModal
        v-model="deleteOpen"
        :title="t('calendarFeeds.deleteConfirmTitle')"
        :message="t('calendarFeeds.deleteConfirmBody', {name: deleteTarget?.name})"
        @confirm="confirmDelete"
    />
  </ViewContent>
</template>
