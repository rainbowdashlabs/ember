/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, shallowRef} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import {documentTemplates, memberGroups, stationMembers, userTags} from '@/api'
import type {MemberGroup, MemberWithName, TemplateUseResponse, UserTag} from '@/api/generated/schema'
import type {RestrictionSelection} from '@/api/types'
import {toRestriction} from '@/components/input/restriction'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {showToast} from '@/util/toast'
import TemplateUsePanel from './templateuseview/TemplateUsePanel.vue'

/**
 * How the station uses a template of its association. The association writes the template and offers
 * it for self service; the station decides whether its members see it there, and who of them. The
 * station's managers generate it for members either way, from the member page. The station also names
 * who of its members issues the template's documents, since the association has no members to name.
 */
const {t} = useI18n()
const route = useRoute()

const templateId = computed(() => Number(route.params.id))
const use = shallowRef<TemplateUseResponse | null>(null)
const selfService = ref(false)
const audience = ref<RestrictionSelection>(toRestriction(null))
const issuerId = ref<number | null>(null)
const issuerFunction = ref('')
const groups = ref<MemberGroup[]>([])
const tags = ref<UserTag[]>([])
const members = ref<MemberWithName[]>([])

function take(loaded: TemplateUseResponse) {
  use.value = loaded
  selfService.value = loaded.selfService
  audience.value = toRestriction(loaded.audience)
  issuerId.value = loaded.issuerId ?? null
  issuerFunction.value = loaded.issuerFunction ?? ''
}

const loader = useAsyncLoader(async () => {
  const [loaded, groupList, tagList, memberList] = await Promise.all([
    documentTemplates.templateUse(templateId.value),
    memberGroups.listGroups().catch(() => []),
    userTags.listTags().catch(() => []),
    stationMembers.listMembers().catch(() => []),
  ])
  take(loaded)
  groups.value = groupList
  tags.value = tagList
  members.value = memberList.filter(member => !member.formerAt)
})

const saving = useAsyncAction(async () => {
  take(await documentTemplates.setTemplateUse(templateId.value, {
    selfService: selfService.value,
    audience: audience.value,
    issuerId: issuerId.value,
    issuerFunction: issuerFunction.value.trim() || null,
  }))
  showToast(t('documentTemplates.use.saved'), 'success')
})

const pageTitle = computed(() => use.value
    ? t('pages.documents-template-use.titleNamed', {name: use.value.name})
    : t('pages.documents-template-use.title'))
</script>

<template>
  <ViewContent :title="pageTitle" :subtitle="t('pages.documents-template-use.subtitle')">
    <Spinner v-if="loader.loading.value" size="lg"/>
    <FailureAlert :failure="loader.failure.value ?? saving.failure.value"/>
    <TemplateUsePanel
        v-if="use"
        v-model:self-service="selfService"
        v-model:audience="audience"
        v-model:issuer-id="issuerId"
        v-model:issuer-function="issuerFunction"
        :use="use"
        :groups="groups"
        :tags="tags"
        :members="members"
        :saving="saving.running.value"
        @save="saving.run()"
    />
  </ViewContent>
</template>
