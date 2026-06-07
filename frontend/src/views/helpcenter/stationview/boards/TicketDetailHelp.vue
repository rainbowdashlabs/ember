/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import IconButton from '@/components/button/IconButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import TabBar from '@/components/navigation/TabBar.vue'
import UserAvatar from '@/components/avatar/UserAvatar.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import LabelSelectInput from '@/components/input/select/LabelSelectInput.vue'

const {t} = useI18n()

const dummyLabels = [
    {id: 1, boardId: 1, name: 'Dringend', color: '#ec2929'},
    {id: 2, boardId: 1, name: 'Ausrüstung', color: '#3694FF'},
]

const dummySelectedLabels = [
    {id: 1, boardId: 1, name: 'Dringend', color: '#ec2929'},
]

const dummyTabs = [
    {key: 'comments', label: 'Kommentare (3)'},
    {key: 'transitions', label: 'Änderungen (5)'},
    {key: 'all', label: 'Alle'},
]
</script>

<template>
  <HelpArticle :title="t('helpCenter.ticketDetail.title')" :subtitle="t('helpCenter.ticketDetail.subtitle')">
    <HelpSection :title="t('helpCenter.ticketDetail.whatIs')">
      <p>{{ t('helpCenter.ticketDetail.whatIsText') }}</p>
      <p>{{ t('helpCenter.ticketDetail.whatIsText2') }}</p>
    </HelpSection>

    <!-- Dummy UI: Ticket header -->
    <HelpSection :title="t('helpCenter.ticketDetail.headerTitle')">
      <p>{{ t('helpCenter.ticketDetail.headerText') }}</p>
      <NeutralContainer>
        <div class="flex items-center gap-3 mb-4">
          <IconButton :icon="['fas', 'chevron-left']" label="Back" />
          <span class="font-mono text-(--text-muted)">JF-42</span>
          <div class="ml-auto flex items-center gap-1">
            <IconButton :icon="['fas', 'eye']" :label="t('boards.watch')" class="text-(--text-muted)" />
            <div class="relative">
              <IconButton :icon="['fas', 'ellipsis']" label="Menu" class="text-(--text-muted)" />
            </div>
          </div>
        </div>
        <div class="text-lg font-semibold rounded-theme px-2 py-1 cursor-pointer hover:bg-(--bg-accent)">
          {{ t('helpCenter.ticketDetail.dummyTitle') }}
        </div>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.ticketDetail.clickToEditTitle')">
      <p>{{ t('helpCenter.ticketDetail.clickToEditText') }}</p>
      <p>{{ t('helpCenter.ticketDetail.clickToEditText2') }}</p>
    </HelpSection>

    <!-- Dummy UI: Full two-column layout -->
    <HelpSection :title="t('helpCenter.ticketDetail.layoutTitle')">
      <p>{{ t('helpCenter.ticketDetail.layoutText') }}</p>
      <NeutralContainer>
        <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
          <!-- Main area -->
          <div class="lg:col-span-2 space-y-4">
            <!-- Add menu -->
            <div class="relative inline-block">
              <IconButton :icon="['fas', 'plus']" label="Add" class="text-(--text-muted)" />
            </div>

            <!-- Description -->
            <div>
              <FieldLabel class="mb-1">{{ t('boards.ticketDescription') }}</FieldLabel>
              <div class="prose prose-sm max-w-none rounded-theme p-2 cursor-pointer hover:bg-(--bg-accent)">
                <p>{{ t('helpCenter.ticketDetail.dummyDescription') }}</p>
              </div>
            </div>

            <!-- Checklist -->
            <NeutralContainer>
              <div class="flex items-center justify-between mb-2">
                <SubHeader>{{ t('boards.checklist') }} (1/3)</SubHeader>
                <IconButton :icon="['fas', 'trash']" :label="t('common.delete')" class="text-xs text-(--text-muted)" />
              </div>
              <div class="h-1.5 bg-(--bg-muted) rounded-full mb-3 overflow-hidden">
                <div class="h-full rounded-full bg-primary" style="width: 33%" />
              </div>
              <div class="flex items-center gap-2 py-0.5">
                <font-awesome-icon :icon="['fas', 'grip-vertical']" class="text-(--text-muted) cursor-grab text-xs" />
                <CheckboxInput :model-value="true" />
                <span class="flex-1 text-sm line-through text-(--text-muted)">{{ t('helpCenter.ticketDetail.checkItem1') }}</span>
                <IconButton :icon="['fas', 'xmark']" label="Remove" class="text-xs" />
              </div>
              <div class="flex items-center gap-2 py-0.5">
                <font-awesome-icon :icon="['fas', 'grip-vertical']" class="text-(--text-muted) cursor-grab text-xs" />
                <CheckboxInput :model-value="false" />
                <span class="flex-1 text-sm">{{ t('helpCenter.ticketDetail.checkItem2') }}</span>
                <IconButton :icon="['fas', 'xmark']" label="Remove" class="text-xs" />
              </div>
              <div class="flex items-center gap-2 py-0.5">
                <font-awesome-icon :icon="['fas', 'grip-vertical']" class="text-(--text-muted) cursor-grab text-xs" />
                <CheckboxInput :model-value="false" />
                <span class="flex-1 text-sm">{{ t('helpCenter.ticketDetail.checkItem3') }}</span>
                <IconButton :icon="['fas', 'xmark']" label="Remove" class="text-xs" />
              </div>
              <div class="flex gap-2 mt-2 items-center">
                <TextInput model-value="" :placeholder="t('boards.addChecklistItem')" class="flex-1 text-sm" />
                <IconButton :icon="['fas', 'plus']" :label="t('common.add')" class="text-(--text-muted)" />
              </div>
            </NeutralContainer>

            <!-- Links section -->
            <div>
              <SubHeader class="text-sm mb-2">{{ t('boards.linkedTickets') }}</SubHeader>
              <div class="mb-2">
                <div class="text-xs font-semibold text-(--text-muted) uppercase tracking-wide mb-1">{{ t('boards.linkBlocks') }}</div>
                <div class="border border-(--border) rounded-theme overflow-hidden">
                  <div class="flex items-center gap-3 px-3 py-2 text-sm">
                    <span class="font-mono text-(--text-muted) shrink-0">JF-15</span>
                    <span class="truncate">{{ t('helpCenter.ticketDetail.linkedTicket1') }}</span>
                  </div>
                </div>
              </div>
              <div class="mb-2">
                <div class="text-xs font-semibold text-(--text-muted) uppercase tracking-wide mb-1">{{ t('boards.linkRelatesTo') }}</div>
                <div class="border border-(--border) rounded-theme overflow-hidden">
                  <div class="flex items-center gap-3 px-3 py-2 text-sm">
                    <span class="font-mono text-(--text-muted) shrink-0">JF-38</span>
                    <span class="truncate">{{ t('helpCenter.ticketDetail.linkedTicket2') }}</span>
                  </div>
                </div>
              </div>
            </div>

            <!-- Weblinks -->
            <div>
              <SubHeader class="text-sm mb-2">{{ t('boards.weblinks') }}</SubHeader>
              <div class="flex items-center gap-2 text-sm">
                <font-awesome-icon :icon="['fas', 'globe']" class="text-(--text-muted) text-xs" />
                <span class="text-(--accent)">{{ t('helpCenter.ticketDetail.weblinkExample') }}</span>
              </div>
            </div>

            <!-- Attachments -->
            <div>
              <SubHeader class="text-sm mb-2">{{ t('boards.attachments') }}</SubHeader>
              <div class="grid grid-cols-3 sm:grid-cols-4 gap-2">
                <div class="relative rounded-lg border border-(--border) overflow-hidden bg-(--bg-accent)" style="aspect-ratio: 3/4">
                  <div class="w-full h-full flex items-center justify-center">
                    <font-awesome-icon :icon="['fas', 'image']" class="text-3xl text-(--text-muted)" />
                  </div>
                  <div class="absolute top-0 left-0 right-0 bg-black/50 px-2 py-1">
                    <p class="text-[0.65rem] text-white truncate">{{ t('helpCenter.ticketDetail.attachmentExample') }}</p>
                  </div>
                </div>
                <div class="relative rounded-lg border border-(--border) overflow-hidden bg-(--bg-accent)" style="aspect-ratio: 3/4">
                  <div class="w-full h-full flex items-center justify-center">
                    <font-awesome-icon :icon="['fas', 'file-pdf']" class="text-3xl text-(--text-muted)" />
                  </div>
                  <div class="absolute top-0 left-0 right-0 bg-black/50 px-2 py-1">
                    <p class="text-[0.65rem] text-white truncate">{{ t('helpCenter.ticketDetail.attachmentPdf') }}</p>
                  </div>
                </div>
              </div>
            </div>

            <!-- KB Links -->
            <div>
              <SubHeader class="text-sm mb-2">{{ t('boards.kbLinks') }}</SubHeader>
              <div class="flex items-center gap-2 text-sm">
                <font-awesome-icon :icon="['fas', 'book']" class="text-(--text-muted) text-xs shrink-0" />
                <span class="text-primary">{{ t('helpCenter.ticketDetail.kbLinkExample') }}</span>
              </div>
            </div>

            <!-- Activity tabs -->
            <TabBar model-value="comments" :tabs="dummyTabs" class="mb-3" />
            <NeutralContainer class="space-y-3">
              <div class="flex gap-2">
                <div class="flex-1">
                  <div class="flex items-center gap-2 text-xs text-(--text-muted)">
                    <MemberName :identity="{stationUid: 's1', memberUid: 'u2', displayTag: {name: 'Lisa Schmidt', color: ''}}" size="sm" class="font-medium" />
                    <span>05.06.26, 14:30</span>
                  </div>
                  <p class="text-sm mt-0.5">{{ t('helpCenter.ticketDetail.commentExample1') }}</p>
                  <div class="ml-6 mt-2 border-l-2 border-(--border) pl-3">
                    <div class="flex items-center gap-2 text-xs text-(--text-muted)">
                      <MemberName :identity="{stationUid: 's1', memberUid: 'u1', displayTag: {name: 'Max Mustermann', color: ''}}" size="sm" class="font-medium" />
                      <span>05.06.26, 15:10</span>
                    </div>
                    <p class="text-sm mt-0.5">{{ t('helpCenter.ticketDetail.commentReply1') }}</p>
                  </div>
                </div>
              </div>
            </NeutralContainer>
          </div>

          <!-- Right sidebar -->
          <div class="space-y-4">
            <!-- Lane -->
            <div class="px-3 py-2 rounded-theme text-sm font-medium text-center text-white cursor-pointer" style="background-color: #3694FF">
              {{ t('helpCenter.ticketDetail.laneInProgress') }}
            </div>

            <!-- Labels -->
            <div>
              <FieldLabel class="mb-1">Labels</FieldLabel>
              <LabelSelectInput :labels="dummyLabels" :selected="dummySelectedLabels" />
            </div>

            <!-- Priority -->
            <div>
              <FieldLabel class="mb-1">{{ t('boards.priority') }}</FieldLabel>
              <div class="flex items-center gap-2 rounded-theme px-2 py-1 text-sm cursor-pointer hover:bg-(--bg-accent)">
                <font-awesome-icon :icon="['fas', 'angle-up']" class="text-orange-500" />
                <span>{{ t('boards.priorityHigh') }}</span>
              </div>
            </div>

            <!-- Assignee -->
            <div>
              <FieldLabel class="mb-1">{{ t('boards.assignee') }}</FieldLabel>
              <div class="flex items-center gap-2 rounded-theme px-2 py-1 text-sm cursor-pointer hover:bg-(--bg-accent)">
                <UserAvatar :identity="{stationUid: 's1', memberUid: 'u1', displayTag: {name: 'Max Mustermann', color: ''}}" size="sm" />
                <span>Max Mustermann</span>
              </div>
            </div>

            <!-- Due date -->
            <div>
              <FieldLabel class="mb-1">{{ t('boards.dueDate') }}</FieldLabel>
              <div class="rounded-theme px-2 py-1 text-sm cursor-pointer hover:bg-(--bg-accent)">
                20.06.2026
              </div>
            </div>

            <!-- Custom fields -->
            <div class="border-t border-(--border) pt-4">
              <FieldLabel class="mb-1">{{ t('helpCenter.ticketDetail.customFieldExample') }}</FieldLabel>
              <TextInput model-value="3" />
            </div>

            <!-- Timestamps -->
            <div class="text-xs text-(--text-muted) space-y-1 pt-4 border-t border-(--border)">
              <p>{{ t('helpCenter.ticketDetail.createdAt') }}: 01.06.26, 09:00</p>
              <p>{{ t('helpCenter.ticketDetail.updatedAt') }}: 05.06.26, 15:10</p>
            </div>
          </div>
        </div>
      </NeutralContainer>
    </HelpSection>

    <!-- Add menu explanation -->
    <HelpSection :title="t('helpCenter.ticketDetail.addMenuTitle')">
      <p>{{ t('helpCenter.ticketDetail.addMenuText') }}</p>
      <NeutralContainer class="w-48">
        <div class="space-y-0">
          <div class="px-3 py-2 text-sm flex items-center gap-2">
            <font-awesome-icon :icon="['fas', 'list-check']" class="text-(--text-muted) w-4" />
            {{ t('boards.checklist') }}
          </div>
          <div class="px-3 py-2 text-sm flex items-center gap-2">
            <font-awesome-icon :icon="['fas', 'link']" class="text-(--text-muted) w-4" />
            {{ t('boards.addLink') }}
          </div>
          <div class="px-3 py-2 text-sm flex items-center gap-2">
            <font-awesome-icon :icon="['fas', 'globe']" class="text-(--text-muted) w-4" />
            {{ t('boards.addWeblink') }}
          </div>
          <div class="px-3 py-2 text-sm flex items-center gap-2">
            <font-awesome-icon :icon="['fas', 'paperclip']" class="text-(--text-muted) w-4" />
            {{ t('boards.addAttachment') }}
          </div>
          <div class="px-3 py-2 text-sm flex items-center gap-2">
            <font-awesome-icon :icon="['fas', 'book']" class="text-(--text-muted) w-4" />
            {{ t('boards.addKbLink') }}
          </div>
        </div>
      </NeutralContainer>
    </HelpSection>

    <!-- Sidebar explanation -->
    <HelpSection :title="t('helpCenter.ticketDetail.sidebarTitle')">
      <p>{{ t('helpCenter.ticketDetail.sidebarText') }}</p>
      <p>{{ t('helpCenter.ticketDetail.sidebarLaneText') }}</p>
    </HelpSection>

    <!-- Link types -->
    <HelpSection :title="t('helpCenter.ticketDetail.linkTypesTitle')">
      <p>{{ t('helpCenter.ticketDetail.linkTypesText') }}</p>
      <ul class="list-disc ml-4 space-y-1">
        <li><strong>{{ t('boards.linkRelatesTo') }}:</strong> {{ t('helpCenter.ticketDetail.linkRelatesDesc') }}</li>
        <li><strong>{{ t('boards.linkBlocks') }} / {{ t('boards.linkBlockedBy') }}:</strong> {{ t('helpCenter.ticketDetail.linkBlocksDesc') }}</li>
        <li><strong>{{ t('boards.linkCauses') }} / {{ t('boards.linkCausedBy') }}:</strong> {{ t('helpCenter.ticketDetail.linkCausesDesc') }}</li>
      </ul>
    </HelpSection>

    <!-- Activity section -->
    <HelpSection :title="t('helpCenter.ticketDetail.activityTitle')">
      <p>{{ t('helpCenter.ticketDetail.activityText') }}</p>
      <ul class="list-disc ml-4 space-y-1">
        <li><strong>{{ t('helpCenter.ticketDetail.tabComments') }}:</strong> {{ t('helpCenter.ticketDetail.tabCommentsDesc') }}</li>
        <li><strong>{{ t('helpCenter.ticketDetail.tabChanges') }}:</strong> {{ t('helpCenter.ticketDetail.tabChangesDesc') }}</li>
        <li><strong>{{ t('helpCenter.ticketDetail.tabAll') }}:</strong> {{ t('helpCenter.ticketDetail.tabAllDesc') }}</li>
      </ul>
    </HelpSection>

    <!-- Watch & delete -->
    <HelpSection :title="t('helpCenter.ticketDetail.watchTitle')">
      <p>{{ t('helpCenter.ticketDetail.watchText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.ticketDetail.deleteTitle')">
      <p>{{ t('helpCenter.ticketDetail.deleteText') }}</p>
    </HelpSection>

    <!-- Delete modal snapshot -->
    <HelpSection :title="t('helpCenter.ticketDetail.deleteModalTitle')">
      <NeutralContainer>
        <SubHeader class="mb-4">{{ t('common.delete') }}</SubHeader>
        <p class="mb-4">{{ t('helpCenter.ticketDetail.deleteConfirm') }}</p>
        <div class="flex justify-end gap-2">
          <DeleteButton>{{ t('common.delete') }}</DeleteButton>
        </div>
      </NeutralContainer>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.ticketDetail.tip') }}</HelpTip>
    <HelpTip>{{ t('helpCenter.ticketDetail.tip2') }}</HelpTip>
  </HelpArticle>
</template>
