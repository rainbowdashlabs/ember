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
import ErrorContainer from '@/components/container/ErrorContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import type {MemberIdentity} from '@/api/types'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.eventsRegistrations.title')" :subtitle="t('helpCenter.eventsRegistrations.subtitle')">
    <HelpSection :title="t('helpCenter.eventsRegistrations.whatShown')">
      <p>{{ t('helpCenter.eventsRegistrations.whatShownText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventsRegistrations.badgeTitle')">
      <p>{{ t('helpCenter.eventsRegistrations.badgeText') }}</p>
    </HelpSection>

    <!-- Dummy: Event-grouped registrations -->
    <HelpSection :title="t('eventsRegistrations.title')">
      <SectionHeader>{{ t('eventsRegistrations.title') }}</SectionHeader>
      <div class="space-y-3">
        <!-- Event group: normal -->
        <NeutralContainer class="space-y-3">
          <div class="flex items-center justify-between flex-wrap gap-2">
            <div>
              <span class="font-medium text-primary">Wettkampf Vorbereitung</span>
              <span class="text-xs text-(--text-muted) ml-2">
                {{ t('eventsRegistrations.deadline') }}: 20.05.2026 18:00
              </span>
              <span class="text-xs text-(--text-muted) ml-2">
                ({{ t('eventsRegistrations.limit') }}: 12)
              </span>
            </div>
            <div class="flex items-center gap-2">
              <InfoBadge>3 {{ t('eventsRegistrations.pending') }}</InfoBadge>
              <SuccessBadge>2 {{ t('eventsRegistrations.accepted') }}</SuccessBadge>
            </div>
          </div>

          <!-- Expanded: fairness table -->
          <div class="border-t border-(--border) pt-3">
            <table class="w-full text-sm">
              <thead>
                <tr class="text-xs text-(--text-muted) border-b border-(--border)">
                  <th class="p-2 text-left">{{ t('eventsRegistrations.member') }}</th>
                  <th class="p-2 text-center">{{ t('eventsRegistrations.score') }}</th>
                  <th class="p-2 text-center">{{ t('eventsRegistrations.acceptedCol') }}</th>
                  <th class="p-2 text-center">{{ t('eventsRegistrations.deniedCol') }}</th>
                  <th class="p-2 text-center">{{ t('eventsRegistrations.date') }}</th>
                  <th class="p-2"></th>
                </tr>
              </thead>
              <tbody>
                <tr class="border-b border-(--border)">
                  <td class="p-2">
                    <MemberName :identity="{stationUid: '', memberUid: '', name: 'Max Mustermann'} as MemberIdentity"/>
                  </td>
                  <td class="p-2 text-center font-bold text-error">8.5</td>
                  <td class="p-2 text-center"><SuccessBadge>3</SuccessBadge></td>
                  <td class="p-2 text-center"><ErrorBadge>2</ErrorBadge></td>
                  <td class="p-2 text-center text-xs text-(--text-muted)">2026-05-25</td>
                  <td class="p-2">
                    <div class="flex items-center gap-1 justify-end">
                      <PrimaryButton compact :icon="['fas', 'check']">{{ t('eventsRegistrations.accept') }}</PrimaryButton>
                      <ErrorButton compact :icon="['fas', 'xmark']">{{ t('eventsRegistrations.deny') }}</ErrorButton>
                    </div>
                  </td>
                </tr>
                <tr class="border-b border-(--border)">
                  <td class="p-2">
                    <MemberName :identity="{stationUid: '', memberUid: '', name: 'Erika Muster'} as MemberIdentity"/>
                  </td>
                  <td class="p-2 text-center font-bold text-info">4.2</td>
                  <td class="p-2 text-center"><SuccessBadge>5</SuccessBadge></td>
                  <td class="p-2 text-center">0</td>
                  <td class="p-2 text-center text-xs text-(--text-muted)">2026-05-25</td>
                  <td class="p-2">
                    <div class="flex items-center gap-1 justify-end">
                      <PrimaryButton compact :icon="['fas', 'check']">{{ t('eventsRegistrations.accept') }}</PrimaryButton>
                      <ErrorButton compact :icon="['fas', 'xmark']">{{ t('eventsRegistrations.deny') }}</ErrorButton>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </NeutralContainer>

        <!-- Event group: expired deadline -->
        <ErrorContainer class="space-y-3">
          <div class="flex items-center justify-between flex-wrap gap-2">
            <div>
              <span class="font-medium text-primary">Erste-Hilfe-Kurs</span>
              <span class="text-xs text-(--text-muted) ml-2">
                {{ t('eventsRegistrations.deadline') }}: 10.05.2026 12:00
              </span>
            </div>
            <div class="flex items-center gap-2">
              <InfoBadge>1 {{ t('eventsRegistrations.pending') }}</InfoBadge>
              <ErrorBadge>{{ t('eventsRegistrations.expired') }}</ErrorBadge>
            </div>
          </div>
        </ErrorContainer>
      </div>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventsRegistrations.fairnessTitle')">
      <p>{{ t('helpCenter.eventsRegistrations.fairnessText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventsRegistrations.deadlineTitle')">
      <p>{{ t('helpCenter.eventsRegistrations.deadlineText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.eventsRegistrations.tip') }}</HelpTip>
  </HelpArticle>
</template>
