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
import EventGroupOverview from './eventgroupdetailview/EventGroupOverview.vue'
import EventGroupHouseholdPanel from './eventgroupdetailview/EventGroupHouseholdPanel.vue'
import EventGroupSignUpsPanel from './eventgroupdetailview/EventGroupSignUpsPanel.vue'
import EventGroupPartnerSignUpsPanel from './eventgroupdetailview/EventGroupPartnerSignUpsPanel.vue'
import EventGroupAttendancePanel from './eventgroupdetailview/EventGroupAttendancePanel.vue'
import EventGroupCancelDialog from './eventgroupdetailview/EventGroupCancelDialog.vue'
import type {EventGroupDetail} from '@/api/generated/schema'
import {StationPermission} from '@/api/generated/schema'
import {eventGroups} from '@/api'
import {useSession} from '@/composables/useSession'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {describeFailure, type Failure} from '@/util/failure'

/**
 * One event group: what it is, its parts by set with each set's minimum, and the sign-up of the reader and
 * everybody they look after. Whoever handles registrations sees every sign-up and decides on them;
 * whoever edits appointments edits, calls off or deletes the event group.
 */
const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const {loaded, hasPermission} = useSession()

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

const {loading, failure, reload} = useAsyncLoader(async () => {
  detail.value = await eventGroups.getEventGroup(eventGroupId.value)
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
    await router.push({name: 'event-groups'})
  } catch (e) {
    actionFailure.value = describeFailure(e, t)
    deleteOpen.value = false
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <ViewContent :title="detail?.eventGroup.name ?? t('pages.event-group.title')" :subtitle="t('pages.event-group.subtitle')">
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
        <EventGroupOverview :detail="detail"/>
        <EventGroupHouseholdPanel :detail="detail" :on-changed="refresh"/>
        <EventGroupSignUpsPanel v-if="canDecide" ref="signUpsPanel" :detail="detail" :on-changed="refresh"/>
        <EventGroupPartnerSignUpsPanel v-if="canDecide" :detail="detail" :on-changed="refresh"/>
        <EventGroupAttendancePanel v-if="canDecide" ref="attendancePanel" :detail="detail"/>
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
