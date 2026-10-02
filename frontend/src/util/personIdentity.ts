/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {MemberIdentity} from '@/api/generated/schema'

/**
 * Whom a name or an avatar draws: a member as the server identifies them, or an account by its
 * address where the person need belong to no station, with the picture itself where the server sent
 * it inline. Every part is optional, because a screen draws whom it knows about, and the avatar falls
 * back to initials when nothing names a picture.
 */
export type PersonIdentity = Partial<MemberIdentity> & {
    accountUid?: string
    avatarUrl?: string | null
}
