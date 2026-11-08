/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {NavEntryDef} from '@/data/sidebar/navTypes'

/** The account's sidebar, in order. Every signed-in person sees all of it. */
export const ACCOUNT_NAV: NavEntryDef<null>[] = [
    {kind: 'page', key: 'account-avatar', label: 'sidebar.accountAvatar', icon: 'image', to: '/account/avatar'},
    {kind: 'page', key: 'account-signature', label: 'sidebar.accountSignature', icon: 'signature', to: '/account/signature'},
    {kind: 'page', key: 'account-theming', label: 'sidebar.accountTheming', icon: 'palette', to: '/account/theming'},
    {kind: 'page', key: 'account-sessions', label: 'sidebar.accountSessions', icon: 'list', to: '/account/sessions'},
    {kind: 'page', key: 'account-security', label: 'sidebar.accountSecurity', icon: 'shield', to: '/account/security'},
    {kind: 'page', key: 'account-links', label: 'sidebar.accountLinks', icon: 'link', to: '/account/links'},
    {kind: 'page', key: 'account-gdpr', label: 'sidebar.accountGdpr', icon: 'shield-halved', to: '/account/gdpr'},
]
