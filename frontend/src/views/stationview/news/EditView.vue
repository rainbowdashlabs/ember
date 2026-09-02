/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import { useI18n } from 'vue-i18n'
import { useRouter, useRoute } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import {StationPermission, type MemberGroup, type StationMember, type UserTag} from '@/api/types'
import type { PartnerResponse } from '@/api/federation'
import { news, memberGroups, userTags, federation, events, stationMembers } from '@/api'
import { useEventRoutes } from '@/composables/useEventRoutes'
import { buildAnnouncementDraft, type AnnouncementDraft } from './editview/announcementPrefill'
import AnnouncementNotice from './editview/AnnouncementNotice.vue'
import ContentPanel from './editview/ContentPanel.vue'
import {ContentMode, type ContentModeName} from '@/api/news'
import type {RowEditData} from '@/components/content/blockeditor/EditorRow.vue'
import type {PageRow, SaveRowRequest, SaveCellRequest} from '@/api/pageManage'
import {markdownAsSingleBlock} from '@/util/blockSwitch'
import AttachmentsPanel from './editview/AttachmentsPanel.vue'
import {useNewsAttachments} from './editview/useNewsAttachments'
import AudiencePanels from './editview/AudiencePanels.vue'
import { useSession } from '@/composables/useSession'

const { t } = useI18n()
const router = useRouter()
const newsRoutes = useNewsRoutes()
const route = useRoute()
const { loaded, hasPermission, sessionInfo } = useSession()
const canFederateNews = () => hasPermission(StationPermission.NEWS_FEDERATE)

const isEdit = computed(() => !!route.params.id)
const newsId = computed(() => isEdit.value ? Number(route.params.id) : null)
const eventRoutes = useEventRoutes()

/**
 * The appointment a new entry announces, and the one occurrence it is about.
 *
 * <p>Both travel in the address rather than in a handover between two screens, so the draft
 * survives a reload and so the audience is read from the server rather than believed from a
 * previous page. Only a new entry takes them: an entry that already exists was written once and
 * does not get rewritten from an appointment behind its author's back.
 */
const announcedEventId = computed(() => {
  if (isEdit.value) return null
  const raw = Array.isArray(route.query.event) ? route.query.event[0] : route.query.event
  const id = Number(raw)
  return raw && Number.isFinite(id) && id > 0 ? id : null
})

const announcedDate = computed(() => {
  const raw = Array.isArray(route.query.date) ? route.query.date[0] : route.query.date
  return raw && /^\d{4}-\d{2}-\d{2}$/.test(raw) ? raw : null
})

const announcement = ref<AnnouncementDraft | null>(null)

const title = ref('')
const contentMarkdown = ref('')
const selectedUserTypes = ref<string[]>([])
const selectedGroupIds = ref<number[]>([])
const selectedTagIds = ref<number[]>([])
const selectedMemberIds = ref<number[]>([])
const groups = ref<MemberGroup[]>([])
const tags = ref<UserTag[]>([])
const members = ref<StationMember[]>([])

const publicBlog = ref(false)
const contentMode = ref<ContentModeName>(ContentMode.SIMPLE)
const rows = ref<RowEditData[]>([])

// Federation sharing
const federationShared = ref(false)
const federationScope = ref('ALL_PARTNERS')
const federationVisibilityRole = ref('MEMBER')
const federationPartnerIds = ref<number[]>([])
const partners = ref<PartnerResponse[]>([])

const stationUid = computed(() => sessionInfo.value?.stationId ?? '')
const {attachments, load: loadAttachments, add: addAttachment, remove: removeAttachment, reorder: reorderAttachments, persist: persistAttachments} = useNewsAttachments()

/**
 * The saved shape of a block tree turned into the shape the editor works on. Ids of zero mark rows
 * and cells that do not exist yet, which is how the save path tells new from moved.
 */
function toEditRows(saved: PageRow[]): RowEditData[] {
    return [...saved]
        .sort((a, b) => a.sortOrder - b.sortOrder)
        .map(r => ({
            id: r.id,
            sortOrder: r.sortOrder,
            cells: [...r.cells]
                .sort((a, b) => a.sortOrder - b.sortOrder)
                .map(c => ({
                    id: c.id,
                    sortOrder: c.sortOrder,
                    widthPercent: c.widthPercent,
                    contentType: c.contentType,
                    content: c.content,
                    config: c.config as Record<string, unknown>,
                })),
        }))
}

function toSaveRows(): SaveRowRequest[] {
    return rows.value.map((r, ri) => ({
        sortOrder: ri,
        cells: r.cells.map((c, ci): SaveCellRequest => ({
            sortOrder: ci,
            widthPercent: c.widthPercent,
            contentType: c.contentType,
            content: c.content,
            config: c.config,
        })),
    }))
}

/**
 * Switches the entry being written to the block editor.
 *
 * <p>An entry that already exists is switched by the server, which is what records the change and
 * hands back the text as a first block. One that does not exist yet cannot be: there is nothing to
 * address. It is switched here instead, to the same shape the server would have produced, and the
 * save below tells the server about it as soon as the entry has an id. Either way the author sees
 * the same thing, which is the point: whether an entry happens to be saved yet is not a reason to
 * withhold the editor from them.
 */
async function enableBlocks() {
    if (!newsId.value) {
        rows.value = markdownAsSingleBlock(contentMarkdown.value)
        contentMode.value = ContentMode.RICH
        return
    }
    const updated = await news.enableNewsBlocks(newsId.value)
    contentMode.value = updated.contentMode
    rows.value = toEditRows(updated.rows ?? [])
    contentMarkdown.value = updated.contentMarkdown
}

const canSave = computed(() =>
    !!title.value.trim() && (contentMode.value === ContentMode.RICH || !!contentMarkdown.value.trim()),
)

/**
 * Whether the entry still names an audience of its own. An entry that does reaches nobody outside
 * this station, whatever the two switches below it say, so lifting the last restriction is the
 * deliberate act that lets an announcement travel.
 */
const stillRestricted = computed(() =>
    selectedUserTypes.value.length > 0
    || selectedGroupIds.value.length > 0
    || selectedTagIds.value.length > 0
    || selectedMemberIds.value.length > 0,
)


/**
 * Reads the appointment the address names and writes the first draft from it.
 *
 * <p>The audience comes from the appointment's own view restriction rather than from anything the
 * previous screen said, so an appointment only some members may know about produces an entry only
 * those members may read. Such an entry reaches no partner station and no public page while it
 * stays restricted, which is what makes announcing a restricted appointment safe to offer at all.
 */
async function loadAnnouncement(eventId: number) {
  const [event, fields, restrictions] = await Promise.all([
    events.getEvent(eventId),
    events.getEventFields(eventId).catch(() => []),
    events.getRestrictions(eventId).catch(() => null),
  ])
  const view = restrictions?.view
  const audience = {
    userTypes: view?.userTypes ?? [],
    groupIds: view?.groupIds ?? [],
    tagIds: view?.tagIds ?? [],
    memberIds: view?.memberIds ?? [],
    mode: 'AND' as const,
  }
  const names = new Map(members.value.map(m => [m.id, m.name ?? m.email ?? `#${m.id}`]))
  const link = announcedDate.value
      ? router.resolve({name: eventRoutes.detailOnDate, params: {id: eventId, date: announcedDate.value}}).href
      : router.resolve({name: eventRoutes.detail, params: {id: eventId}}).href
  const draft = buildAnnouncementDraft(
      event,
      announcedDate.value,
      fields,
      audience,
      names,
      {
        when: t('news.announcement.when'),
        until: t('news.announcement.until'),
        linkLabel: t('news.announcement.linkLabel'),
        yes: t('common.yes'),
        no: t('common.no'),
      },
      link,
  )
  announcement.value = draft
  title.value = draft.title
  contentMarkdown.value = draft.markdown
  selectedUserTypes.value = [...draft.audience.userTypes]
  selectedGroupIds.value = [...draft.audience.groupIds]
  selectedTagIds.value = [...draft.audience.tagIds]
  selectedMemberIds.value = [...draft.audience.memberIds]
}

const { loading, error, reload } = useAsyncLoader(async () => {
  const [groupList, tagList, memberList] = await Promise.all([
    memberGroups.listGroups(),
    userTags.listTags(),
    stationMembers.listCompletions().catch(() => []),
  ])
  groups.value = groupList
  tags.value = tagList
  members.value = memberList.map(m => ({id: m.id, stationId: '', accountId: 0, name: m.name}))
  if (canFederateNews()) {
    partners.value = (await federation.listPartners()).filter(p => p.partner.status === 'ACTIVE')
  }

  if (newsId.value) {
    const entry = await news.getNews(newsId.value)
    title.value = entry.title
    contentMarkdown.value = entry.contentMarkdown
    selectedUserTypes.value = entry.userTypes ?? []
    selectedGroupIds.value = entry.groupIds ?? []
    selectedTagIds.value = entry.tagIds ?? []
    selectedMemberIds.value = entry.memberIds ?? []
    publicBlog.value = entry.publicBlog ?? false
    contentMode.value = entry.contentMode ?? ContentMode.SIMPLE
    rows.value = toEditRows(entry.rows ?? [])
    loadAttachments(entry.attachments ?? [])

    if (canFederateNews()) {
      const fedShare = await news.getFederationShare(newsId.value)
      federationShared.value = fedShare.shared
      if (fedShare.shared) {
        federationScope.value = fedShare.scope ?? 'ALL_PARTNERS'
        federationVisibilityRole.value = fedShare.visibilityRole ?? 'MEMBER'
        federationPartnerIds.value = fedShare.partnerIds ?? []
      }
    }
  } else if (announcedEventId.value) {
    await loadAnnouncement(announcedEventId.value)
  }
}, {autoLoad: false})

async function save() {
  if (!title.value.trim()) return
  if (contentMode.value === ContentMode.SIMPLE && !contentMarkdown.value.trim()) return
  error.value = ''
  try {
    const data = {
      title: title.value,
      contentMarkdown: contentMarkdown.value,
      userTypes: selectedUserTypes.value,
      groupIds: selectedGroupIds.value,
      tagIds: selectedTagIds.value,
      memberIds: selectedMemberIds.value,
      publicBlog: publicBlog.value,
      contentMode: contentMode.value,
    }
    let savedId: number
    if (newsId.value) {
      await news.updateNews(newsId.value, data)
      savedId = newsId.value
    } else {
      // The mode goes along with the entry, so one switched before it existed is created as a
      // block entry outright. Creating it plain and switching after would leave a moment where
      // the entry claims to be something it is not.
      const created = await news.createNews(data)
      savedId = created.id
    }

    if (contentMode.value === ContentMode.RICH) {
      const updated = await news.saveNewsBlocks(savedId, toSaveRows())
      contentMarkdown.value = updated.contentMarkdown
    }

    await persistAttachments(savedId)

    if (canFederateNews()) {
      if (federationShared.value) {
        const pIds = federationScope.value === 'SPECIFIC_PARTNERS' ? federationPartnerIds.value : undefined
        await news.setFederationShare(savedId, federationScope.value, federationVisibilityRole.value, pIds)
      } else {
        await news.removeFederationShare(savedId).catch(() => {})
      }
    }

    await router.push({ name: newsRoutes.list })
  } catch (e) {
    error.value = t('common.error')
    throw e
  }
}

watch(loaded, (isLoaded) => {
  if (isLoaded) reload()
}, {immediate: true})
</script>

<template>
  <ViewContent
      :title="t('pages.news-create.title')"
      :subtitle="t('pages.news-create.subtitle')"
  >
    <div class="space-y-6">
      <SecondaryButton :icon="['fas', 'chevron-left']" @click="router.push({ name: newsRoutes.list })">
        {{ t('common.back') }}
      </SecondaryButton>

      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading">
        <AnnouncementNotice v-if="announcement" :draft="announcement" :public-blog="publicBlog"
                            :federated="federationShared" :restricted="stillRestricted"/>
        <ContentPanel
            v-model:title="title"
            v-model:content-markdown="contentMarkdown"
            v-model:rows="rows"
            :mode="contentMode"
            :station-uid="stationUid"
            @enable-blocks="enableBlocks"
        />
        <AttachmentsPanel
            v-model:attachments="attachments"
            :station-uid="stationUid"
            @add="addAttachment"
            @remove="removeAttachment"
            @reorder="reorderAttachments"
        />
        <AudiencePanels
            v-model:public-blog="publicBlog"
            v-model:selected-user-types="selectedUserTypes"
            v-model:selected-group-ids="selectedGroupIds"
            v-model:selected-tag-ids="selectedTagIds"
            v-model:selected-member-ids="selectedMemberIds"
            v-model:federation-shared="federationShared"
            v-model:federation-scope="federationScope"
            v-model:federation-partner-ids="federationPartnerIds"
            v-model:federation-visibility-role="federationVisibilityRole"
            :groups="groups"
            :tags="tags"
            :members="members"
            :partners="partners"
            :can-federate="canFederateNews()"
        />

        <div class="flex justify-end gap-3">
          <SecondaryButton @click="router.push({ name: newsRoutes.list })">{{ t('common.cancel') }}</SecondaryButton>
          <SaveButton :disabled="!canSave" :action="save"/>
        </div>
      </template>
    </div>
  </ViewContent>
</template>
