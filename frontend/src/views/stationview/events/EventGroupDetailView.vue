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
import ButtonRow from '@/components/button/ButtonRow.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
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
import {StationPermission} from '@/api/generated/schema'
import {eventGroups, stationMembers} from '@/api'
import {eventGroupCommentSource} from '@/api/eventGroups'
import {useSession} from '@/composables/useSession'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {describeFailure, type Failure} from '@/util/failure'

/**
 * One event group: what it is, its parts by set with each set's minimum, the sign-up of the reader and
 * everybody they look after, and its comments. Whoever handles registrations sees every sign-up and
 * decides on them; whoever edits appointments edits, calls off or deletes the event group and keeps
 * its notes; whoever reads the waiting lists sees who comes to a part for a probe.
 */
const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const {loaded, hasPermission, sessionInfo} = useSession()

const eventGroupId = computed(() => Number(route.params.eventGroupId))
const detail = ref<EventGroupDetail | null>(null)
const actionFailure = ref<Failure | null>(null)
const cancelOpen = ref(false)
const deleteOpen = ref(false)
const busy = ref(false)
const signUpsPanel = ref<InstanceType<typeof EventGroupSignUpsPanel> | null>(null)
const attendancePanel = ref<InstanceType<typeof EventGroupAttendancePanel> | null>(null)

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
  await attendancePanel.value?.reload()
}

async function cancelEventGroup(reason: string | null) {
  busy.value = true
  try {
    detail.value = await eventGroups.cancelEventGroup(eventGroupId.value, reason)
    cancelOpen.value = false
  } catch (e) {
    actionFailure.value = describeFailure(e, t)
    cancelOpen.value = false
  } finally {
    busy.value = false
  }
}

async function deleteEventGroup() {
  busy.value = true
  try {
    await eventGroups.deleteEventGroup(eventGroupId.value)
    await router.push({name: 'events'})
  } catch (e) {
    actionFailure.value = describeFailure(e, t)
    deleteOpen.value = false
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <ViewContent :title="detail?.eventGroup.details.name ?? t('pages.event-group.title')" :subtitle="t('pages.event-group.subtitle')">
    <AsyncSection :loading="loading && !detail" :failure="failure">
      <div v-if="detail" class="space-y-6">
        <FailureAlert v-if="actionFailure" :failure="actionFailure"/>
        <ButtonRow v-if="canEdit" align="end">
          <SecondaryButton :icon="['fas', 'pen']" :to="{name: 'event-group-edit', params: {eventGroupId}}" data-testid="event-group-edit">
            {{ t('eventGroups.edit') }}
          </SecondaryButton>
          <SecondaryButton v-if="!detail.eventGroup.cancelled" :icon="['fas', 'ban']" data-testid="event-group-cancel" @click="cancelOpen = true">
            {{ t('eventGroups.cancel') }}
          </SecondaryButton>
          <ErrorButton :icon="['fas', 'trash']" data-testid="event-group-delete" @click="deleteOpen = true">
            {{ t('eventGroups.delete') }}
          </ErrorButton>
        </ButtonRow>
        <TabBar v-model="activeTab" :tabs="tabs" linked/>
        <div v-show="activeTab === 'info'" class="space-y-6" data-testid="event-group-tab-info">
          <EventGroupOverview :detail="detail" :current-member-id="currentMemberId" :member-names="memberNames" :on-changed="refresh"/>
          <EventGroupHouseholdPanel :detail="detail" :on-changed="refresh"/>
          <EventGroupMaterialsPanel :event-group-id="detail.eventGroup.id"/>
          <NeutralContainer v-if="canEdit">
            <NoteEditor entity-type="EVENT_GROUP" :entity-id="eventGroupId"/>
          </NeutralContainer>
          <NeutralContainer>
            <CommentSection :source="commentSource"/>
          </NeutralContainer>
        </div>
        <div v-if="canDecide || readsWaitingLists" v-show="activeTab === 'sign-up'" class="space-y-6" data-testid="event-group-tab-sign-up">
          <EventGroupSignUpsPanel v-if="canDecide" ref="signUpsPanel" :detail="detail" :on-changed="refresh"/>
          <EventGroupPartnerSignUpsPanel v-if="canDecide" :detail="detail" :on-changed="refresh"/>
          <AgreementSignersPanel v-if="canDecide" :event-group-id="detail.eventGroup.id"/>
          <EventGroupProbeVisitorsPanel v-if="readsWaitingLists" :parts="detail.parts"/>
        </div>
        <div v-if="canDecide" v-show="activeTab === 'attendance'" data-testid="event-group-tab-attendance">
          <EventGroupAttendancePanel ref="attendancePanel" :detail="detail"/>
        </div>
      </div>
    </AsyncSection>
    <EventGroupCancelDialog v-model="cancelOpen" :busy="busy" @confirm="cancelEventGroup"/>
    <ConfirmDeleteModal
        v-model="deleteOpen"
        :title="t('eventGroups.deleteTitle')"
        :message="t('eventGroups.deleteText')"
        :busy="busy"
        @confirm="deleteEventGroup"
    />
  </ViewContent>
</template>
