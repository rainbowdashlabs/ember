/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import BulletList from '@/components/typography/BulletList.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import ConversationRow from '@/views/messagesview/conversationlist/ConversationRow.vue'
import MessageTimeline from '@/views/messagesview/conversationpane/MessageTimeline.vue'
import MessageActionBar from '@/views/messagesview/conversationpane/MessageActionBar.vue'
import {timelineOf} from '@/util/messageTimeline'
import {exampleConversations, exampleMessages, READER_UID, STILL_ACTIONS} from './messagesHelpExamples'

const {t} = useI18n()

const conversations = computed(() => exampleConversations(t))
const messages = computed(() => exampleMessages(t))
const items = computed(() => timelineOf(messages.value, 2, READER_UID))
const answer = computed(() => messages.value[2])

function nothingOlder(): Promise<void> {
  return Promise.resolve()
}
</script>

<template>
  <HelpArticle :title="t('helpCenter.messages.title')" :subtitle="t('helpCenter.messages.subtitle')">
    <HelpSection :title="t('helpCenter.messages.whatIs')">
      <p>{{ t('helpCenter.messages.whatIsText') }}</p>
      <p>{{ t('helpCenter.messages.whoText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.messages.howTo')">
      <BulletList>
        <li>{{ t('helpCenter.messages.step1') }}</li>
        <li>{{ t('helpCenter.messages.step2') }}</li>
        <li>{{ t('helpCenter.messages.step3') }}</li>
        <li>{{ t('helpCenter.messages.step4') }}</li>
      </BulletList>
    </HelpSection>

    <HelpSection :title="t('helpCenter.messages.listTitle')">
      <p>{{ t('helpCenter.messages.listText') }}</p>
      <NeutralContainer class="space-y-1">
        <ConversationRow v-for="conversation in conversations" :key="conversation.uid"
                         :conversation="conversation" :selected="false"/>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.messages.conversationTitle')">
      <p>{{ t('helpCenter.messages.conversationText') }}</p>
      <NeutralContainer class="flex max-h-96 flex-col">
        <MessageTimeline :items="items" :has-older="false" :actions="STILL_ACTIONS" :load-older="nothingOlder"/>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.messages.actionsTitle')">
      <p>{{ t('helpCenter.messages.actionsText') }}</p>
      <div v-if="answer" class="w-fit">
        <MessageActionBar :message="answer" :actions="STILL_ACTIONS"/>
      </div>
      <p>{{ t('helpCenter.messages.reactionsText') }}</p>
      <p>{{ t('helpCenter.messages.phoneText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.messages.groupsTitle')">
      <p>{{ t('helpCenter.messages.groupsText') }}</p>
      <p>{{ t('helpCenter.messages.ownerText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.messages.quietTitle')">
      <p>{{ t('helpCenter.messages.muteText') }}</p>
      <p>{{ t('helpCenter.messages.mailText') }}</p>
      <p>{{ t('helpCenter.messages.blockText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.messages.privacyTitle')">
      <p>{{ t('helpCenter.messages.noReportText') }}</p>
      <p>{{ t('helpCenter.messages.noEncryptionText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.messages.tip') }}</HelpTip>
  </HelpArticle>
</template>
