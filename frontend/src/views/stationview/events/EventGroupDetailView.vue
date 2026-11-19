/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import AsyncSection from '@/components/feedback/AsyncSection.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import DropdownMenuItem from '@/components/button/DropdownMenuItem.vue'
import DetailPageHeader from './eventshared/DetailPageHeader.vue'
import TabBar from '@/components/navigation/TabBar.vue'
import EventGroupOverview from './eventgroupdetailview/EventGroupOverview.vue'
import EventGroupMaterialsPanel from './eventgroupdetailview/EventGroupMaterialsPanel.vue'
import AgreementSignersPanel from './eventdetailview/AgreementSignersPanel.vue'
import EventGroupHouseholdPanel from './eventgroupdetailview/EventGroupHouseholdPanel.vue'
import EventGroupSignUpsPanel from './eventgroupdetailview/EventGroupSignUpsPanel.vue'
import EventGroupPartnerSignUpsPanel from './eventgroupdetailview/EventGroupPartnerSignUpsPanel.vue'
import EventGroupAttendancePanel from './eventgroupdetailview/EventGroupAttendancePanel.vue'
import EventGroupCancelDialog from './eventgroupdetailview/EventGroupCancelDialog.vue'
import EventGroupProbeVisitorsPanel from './eventgroupdetailview/EventGroupProbeVisitorsPanel.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import CommentSection from '@/components/comment/CommentSection.vue'
import NoteEditor from '@/components/comment/NoteEditor.vue'
import type {EventGroupDetail, MemberCompletion} from '@/api/generated/schema'
import {NoteEntityType, StationPermission} from '@/api/generated/schema'
import {eventGroups, stationMembers} from '@/api'
import {eventGroupCommentSource} from '@/api/eventGroups'
import {useSession} from '@/composables/useSession'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useAsyncAction} from '@/composables/useAsyncAction'

/**
 * One event group: what it is, its parts by set with each set's minimum, the sign-up of the reader and
 * everybody they look after, and its comments. Whoever handles registrations sees every sign-up and
 * decides on them; whoever edits appointments edits, calls off or deletes the event group from the head of
 * the page, which has the same shape as an appointment's, and keeps its notes; whoever reads the waiting
 * lists sees who comes to a part for a probe.
 */
const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const {loaded, hasPermission, sessionInfo} = useSession()

const eventGroupId = computed(() => Number(route.params.eventGroupId))
const detail = ref<EventGroupDetail | null>(null)
const cancelOpen = ref(false)
const signUpsPanel = ref<InstanceType<typeof EventGroupSignUpsPanel> | null>(null)
const attendancePanel = ref<InstanceType<typeof EventGroupAttendancePanel> | null>(null)
const partnerSignUpsPanel = ref<InstanceType<typeof EventGroupPartnerSignUpsPanel> | null>(null)

const canEdit = computed(() => hasPermission(StationPermission.EVENT_EDIT))
const canDecide = computed(() => hasPermission(StationPermission.EVENT_REGISTRATION))

/**
 * The page in three tabs, as an appointment's page is: what the event group is together with the reader's
 * own sign-up, everybody's sign-ups, and how the participants attended. The sign-ups are for whoever
 * handles registrations or reads the waiting lists, attendance for whoever handles registrations, and the
 * others never see those tabs.
 */
const activeTab = ref<'info' | 'sign-up' | 'attendance'>('info')
const readsWaitingLists = computed(() => hasPermission(StationPermission.WAITLIST_READ))
const tabs = computed(() => {
  const entries = [{key: 'info', label: t('eventGroups.tabInfo')}]
  if (canDecide.value || readsWaitingLists.value) entries.push({key: 'sign-up', label: t('eventGroups.tabSignUp')})
  if (canDecide.value) entries.push({key: 'attendance', label: t('eventGroups.tabAttendance')})
  return entries
})
const commentSource = computed(() => eventGroupCommentSource(eventGroupId.value))

const members = ref<MemberCompletion[]>([])
const currentMemberId = computed(() => sessionInfo.value?.member?.id)

/** The station's members by id, under the name a reader knows them by, for the member fields. */
const memberNames = computed(() =>
    new Map(members.value.map(member => [member.id, member.name] as const)))

const {loading, failure, reload} = useAsyncLoader(async () => {
  const [loadedDetail, completions] = await Promise.all([
    eventGroups.getEventGroup(eventGroupId.value),
    members.value.length > 0 ? Promise.resolve(members.value) : stationMembers.listCompletions().catch(() => []),
  ])
  detail.value = loadedDetail
  members.value = completions
}, {autoLoad: loaded.value})

watch(loaded, ready => { if (ready) reload() })

/** Reloads the event group and the managers' list after a sign-up or decision changed them. */
async function refresh() {
  await reload()
  await signUpsPanel.value?.reload()
  await partnerSignUpsPanel.value?.reload()
  await attendancePanel.value?.reload()
}

const {running: cancelling, failure: cancelFailure, run: runCancel} = useAsyncAction(async (reason: string | null) => {
  detail.value = await eventGroups.cancelEventGroup(eventGroupId.value, reason)
})

/** Deletes the event group and leaves for the appointment list, for whoever edits appointments. */
const remove = computed(() => canEdit.value
  ? async () => {
    await eventGroups.deleteEventGroup(eventGroupId.value)
    await router.push({name: 'events'})
  }
  : null)

/** Cancels the event group and closes the dialog, whether the cancelling worked or not. */
async function cancelEventGroup(reason: string | null) {
  await runCancel(reason)
  cancelOpen.value = false
}
</script>

<template>
  <ViewContent :title="detail?.eventGroup.details.name ?? t('pages.event-group.title')" :subtitle="t('pages.event-group.subtitle')">
    <AsyncSection :loading="loading && !detail" :failure="failure">
      <div v-if="detail" class="space-y-6">
        <FailureAlert v-if="cancelFailure" :failure="cancelFailure"/>
        <DetailPageHeader
            :back-to="{name: 'events'}"
            :edit-to="canEdit ? {name: 'event-group-edit', params: {eventGroupId}} : null"
            :edit-label="t('eventGroups.edit')"
            edit-test-id="event-group-edit"
            menu-test-id="event-group-actions"
            :has-menu-items="canEdit && !detail.eventGroup.cancelled"
            :remove="remove"
            :delete-label="t('eventGroups.delete')"
            :delete-title="t('eventGroups.deleteTitle')"
            :delete-message="t('eventGroups.deleteText')"
            delete-test-id="event-group-delete"
        >
          <DropdownMenuItem v-if="canEdit && !detail.eventGroup.cancelled" :icon="['fas', 'ban']" destructive
                            data-testid="event-group-cancel" @click="cancelOpen = true">
            {{ t('eventGroups.cancel') }}
          </DropdownMenuItem>
        </DetailPageHeader>
        <TabBar v-model="activeTab" :tabs="tabs" linked/>
        <div v-show="activeTab === 'info'" class="space-y-6" data-testid="event-group-tab-info">
          <EventGroupOverview :detail="detail" :current-member-id="currentMemberId" :member-names="memberNames" :on-changed="refresh"/>
          <EventGroupHouseholdPanel :detail="detail" :on-changed="refresh"/>
          <EventGroupMaterialsPanel :event-group-id="detail.eventGroup.id"/>
          <NeutralContainer v-if="canEdit">
            <NoteEditor :entity-type="NoteEntityType.EVENT_GROUP" :entity-id="eventGroupId"/>
          </NeutralContainer>
          <NeutralContainer>
            <CommentSection :source="commentSource"/>
          </NeutralContainer>
        </div>
        <div v-if="canDecide || readsWaitingLists" v-show="activeTab === 'sign-up'" class="space-y-6" data-testid="event-group-tab-sign-up">
          <EventGroupSignUpsPanel v-if="canDecide" ref="signUpsPanel" :detail="detail" :on-changed="refresh"/>
          <EventGroupPartnerSignUpsPanel v-if="canDecide" ref="partnerSignUpsPanel" :detail="detail" :on-changed="refresh"/>
          <AgreementSignersPanel v-if="canDecide" :event-group-id="detail.eventGroup.id"/>
          <EventGroupProbeVisitorsPanel v-if="readsWaitingLists" :parts="detail.parts"/>
        </div>
        <div v-if="canDecide" v-show="activeTab === 'attendance'" data-testid="event-group-tab-attendance">
          <EventGroupAttendancePanel ref="attendancePanel" :detail="detail"/>
        </div>
      </div>
    </AsyncSection>
    <EventGroupCancelDialog v-model="cancelOpen" :busy="cancelling" @confirm="cancelEventGroup"/>
  </ViewContent>
</template>
