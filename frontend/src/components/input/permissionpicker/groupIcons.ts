/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/**
 * The icon shown beside each top-level permission group in the picker. A group without an entry
 * falls back to a generic icon, so a new permission does not have to be added here to work.
 */
export const GROUP_ICONS: Record<string, string[]> = {
  STATION_ADMINISTRATOR: ['fas', 'user-shield'],
  LOGIN: ['fas', 'right-to-bracket'],
  ATTENDANCE_MANAGER: ['fas', 'clipboard-check'],
  INVENTORY_MANAGER: ['fas', 'boxes-stacked'],
  EVENT_MANAGER: ['fas', 'calendar-days'],
  MEMBER_MANAGER: ['fas', 'users'],
  WAITLIST_MANAGER: ['fas', 'clock'],
  NEWS_MANAGER: ['fas', 'newspaper'],
  POLL_MANAGER: ['fas', 'square-poll-vertical'],
  LOST_AND_FOUND_MANAGER: ['fas', 'magnifying-glass'],
  CHECKLIST_MANAGER: ['fas', 'square-check'],
  TEST_MANAGER: ['fas', 'graduation-cap'],
  PROTOCOL_MANAGER: ['fas', 'clipboard-list'],
  BOARD_MANAGER: ['fas', 'table-columns'],
  KNOWLEDGE_MANAGER: ['fas', 'book'],
  PAGE_MANAGER: ['fas', 'file-lines'],
  PROCEDURE_MANAGER: ['fas', 'list-check'],
  STATION_MANAGER: ['fas', 'gear'],
  NEWS_FEDERATE: ['fas', 'share-nodes'],
}
