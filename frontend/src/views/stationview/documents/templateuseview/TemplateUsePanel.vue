/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import Alert from '@/components/feedback/Alert.vue'
import LabelledField from '@/components/input/LabelledField.vue'
import RestrictionPicker from '@/components/input/RestrictionPicker.vue'
import ToggleSetting from '@/components/input/toggle/ToggleSetting.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {MemberGroup, MemberWithName, TemplateUseResponse, UserTag} from '@/api/generated/schema'
import type {RestrictionSelection} from '@/api/types'
import {STATION_TEMPLATE_SCREENS} from '../templateScreens'

/**
 * The station's choice for one template of its association: whether its members generate it for
 * themselves, and who of them. The choice only takes effect while the association offers the template
 * for self service, which the panel says where it does not.
 */
const selfService = defineModel<boolean>('selfService', {required: true})
const audience = defineModel<RestrictionSelection>('audience', {required: true})

defineProps<{
  use: TemplateUseResponse
  groups: MemberGroup[]
  tags: UserTag[]
  members: MemberWithName[]
  saving: boolean
}>()

const emit = defineEmits<{
  save: []
}>()

const {t} = useI18n()
const router = useRouter()
</script>

<template>
  <NeutralContainer class="space-y-4">
    <div class="flex flex-wrap items-center gap-2">
      <SubHeader>{{ use.name }}</SubHeader>
      <SecondaryBadge>{{ t('documentTemplates.ofAssociation') }}</SecondaryBadge>
      <SecondaryBadge>{{ t(`documentTemplates.kind.${use.kind}`) }}</SecondaryBadge>
    </div>
    <MutedText size="sm" tag="p">{{ t('documentTemplates.use.intro') }}</MutedText>
    <Alert v-if="!use.offered" variant="info">{{ t('documentTemplates.use.notOffered') }}</Alert>
    <ToggleSetting v-model="selfService" :label="t('documentTemplates.selfService')"
                   :hint="t('documentTemplates.use.cooldown', {days: use.cooldownDays})" data-testid="template-use-self-service"/>
    <LabelledField v-if="selfService" :label="t('documentTemplates.audience')" :help="t('documentTemplates.audienceHelp')">
      <RestrictionPicker v-model="audience" :groups="groups" :tags="tags" :members="members" show-members/>
    </LabelledField>
    <ButtonRow align="end">
      <SecondaryButton :icon="['fas', 'arrow-left']" @click="router.push({name: STATION_TEMPLATE_SCREENS.listRoute})">
        {{ t('documentTemplates.backToList') }}
      </SecondaryButton>
      <PrimaryButton :icon="['fas', 'floppy-disk']" :disabled="saving" data-testid="template-use-save" @click="emit('save')">
        {{ t('documentTemplates.save') }}
      </PrimaryButton>
    </ButtonRow>
  </NeutralContainer>
</template>
