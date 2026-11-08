/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import LinkButton from '@/components/button/LinkButton.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import MutedText from '@/components/typography/MutedText.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import RestrictionPicker from '@/components/input/RestrictionPicker.vue'
import {StationPermission, type MemberGroup, type UserTag} from '@/api/generated/schema'
import type {RestrictionSelection} from '@/api/types'
import {getWitnessReach, setWitnessReach} from '@/api/witnessSigning'
import {audienceLists} from '@/components/documents/generation'
import {toRestriction} from '@/components/input/restriction'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useSession} from '@/composables/useSession'

/**
 * Whom the station's witnesses reach, next to the permission to collect signatures in person. One audience
 * holds for the whole station, however somebody holds the permission. Without one the witnesses reach every
 * member and trial user; an audience narrows them to whom it names. It opens on demand, is read by whoever
 * sees the permissions and changed only by whoever manages members.
 */
const {t} = useI18n()
const {hasPermission} = useSession()

const open = ref(false)
const audience = ref<RestrictionSelection>(toRestriction(null))
const groups = ref<MemberGroup[]>([])
const tags = ref<UserTag[]>([])

const editable = computed(() => hasPermission(StationPermission.MEMBER_MANAGER))
const narrowed = computed(() => [audience.value.userTypes, audience.value.groupIds, audience.value.tagIds, audience.value.memberIds]
    .some(part => part.length > 0))

const loader = useAsyncLoader(async (isCurrent) => {
  const [reach, lists] = await Promise.all([getWitnessReach(), audienceLists()])
  if (!isCurrent()) return
  audience.value = toRestriction(reach)
  groups.value = lists.groups
  tags.value = lists.tags
}, {autoLoad: false})

function toggle() {
  open.value = !open.value
  if (open.value) void loader.reload()
}

async function save() {
  audience.value = toRestriction(await setWitnessReach(audience.value))
}
</script>

<template>
  <div class="space-y-2 mt-1" data-testid="witness-reach" @click.stop>
    <LinkButton data-testid="witness-reach-toggle" @click="toggle">{{ t('permissions.witnessReach.toggle') }}</LinkButton>
    <template v-if="open">
      <Spinner v-if="loader.loading.value" size="sm"/>
      <FailureAlert v-else-if="loader.failure.value" :failure="loader.failure.value"/>
      <template v-else>
        <MutedText size="sm" tag="p" data-testid="witness-reach-summary">
          {{ narrowed ? t('permissions.witnessReach.narrowed') : t('permissions.witnessReach.everybody') }}
        </MutedText>
        <template v-if="editable">
          <RestrictionPicker v-model="audience" :groups="groups" :tags="tags"/>
          <ButtonRow align="end">
            <SaveButton :action="save" compact data-testid="witness-reach-save"/>
          </ButtonRow>
        </template>
      </template>
    </template>
  </div>
</template>
