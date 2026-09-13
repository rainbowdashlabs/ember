/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import DateTimeInput from '@/components/input/datetime/DateTimeInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MemberSelectInput from '@/components/input/select/MemberSelectInput.vue'
import {fromMember, userTypesOf} from '@/components/input/select/memberOption'
import type {StationMember} from '@/api/types'

const props = defineProps<{
  members: StationMember[]
}>()

const emit = defineEmits<{
  grant: [memberId: number, closesAt: string | null]
}>()

const {t} = useI18n()

const accessMemberId = ref('')
const accessClosesAt = ref('')

const options = computed(() => props.members.map(fromMember))
const userTypes = computed(() => userTypesOf(options.value))

function grantAccess() {
  const memberId = Number(accessMemberId.value)
  if (!memberId) return
  emit('grant', memberId, accessClosesAt.value ? new Date(accessClosesAt.value).toISOString() : null)
  accessMemberId.value = ''
  accessClosesAt.value = ''
}
</script>

<template>
  <div class="space-y-3">
    <SubHeader>{{ t('quiz.tests.accessManagement') }}</SubHeader>
    <NeutralContainer>
      <div class="flex flex-col sm:flex-row gap-3 items-start sm:items-end">
        <div class="flex-1">
          <FieldLabel hint class="mb-1">{{ t('quiz.tests.grantAccessMember') }}</FieldLabel>
          <MemberSelectInput
              v-model="accessMemberId"
              :members="options"
              :user-types="userTypes"
              :placeholder="t('quiz.tests.searchMember')"
          />
        </div>
        <div>
          <FieldLabel hint class="mb-1">{{ t('quiz.tests.accessClosesAt') }}</FieldLabel>
          <DateTimeInput v-model="accessClosesAt" />
        </div>
        <PrimaryButton :disabled="!accessMemberId" @click="grantAccess">
          {{ t('quiz.tests.grantAccess') }}
        </PrimaryButton>
      </div>
    </NeutralContainer>
  </div>
</template>
