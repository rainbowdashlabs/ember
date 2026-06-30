/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.feature.events.entity.EventField;
import dev.chojo.ember.feature.events.entity.MemberFieldValue;
import dev.chojo.ember.feature.events.repository.EventFieldRepository;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.UserTagService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.ConflictResponse;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.NotFoundResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class EventFieldService {
    private static final Logger log = LoggerFactory.getLogger(EventFieldService.class);

    private final EventFieldRepository repository;
    private final StationMemberRepository memberRepository;
    private final MemberGroupRepository groupRepository;
    private final UserTagService tagService;

    @Inject
    public EventFieldService(
            EventFieldRepository repository,
            StationMemberRepository memberRepository,
            MemberGroupRepository groupRepository,
            UserTagService tagService) {
        this.repository = repository;
        this.memberRepository = memberRepository;
        this.groupRepository = groupRepository;
        this.tagService = tagService;
    }

    public List<String> findDistinctFieldNames(int stationId) {
        return repository.findDistinctFieldNames(stationId);
    }

    public List<EventField> findByEvent(int eventId) {
        return repository.findByEvent(eventId);
    }

    public Map<Integer, List<EventField>> findOverviewFieldsByEvents(List<Integer> eventIds) {
        var allFields = repository.findOverviewFieldsByEvents(eventIds);
        var result = new LinkedHashMap<Integer, List<EventField>>();
        for (var field : allFields) {
            result.computeIfAbsent(field.eventId(), _ -> new ArrayList<>()).add(field);
        }
        return result;
    }

    public void replaceFields(int eventId, List<EventFieldRepository.FieldEntry> fields) {
        repository.replaceFields(eventId, fields);
        log.info("Replaced {} fields for event {}", fields.size(), eventId);
    }

    /**
     * Toggles the given member in or out of a MEMBER-type field whose
     * {@code selfRegistration} flag is set.
     *
     * <p>List variants ({@code MEMBER_LIST*}) add the member when absent and remove
     * them when present. Single-value variants take the slot when empty, clear it
     * when the caller already holds it, and raise {@link ConflictResponse} when the
     * slot belongs to someone else.
     *
     * @throws NotFoundResponse   when the field does not exist on the given event
     * @throws BadRequestResponse when the field is not a member-type field, when
     *                            self-registration is not enabled, or when the
     *                            field's required constraint is mis-configured
     * @throws ForbiddenResponse  when the caller does not satisfy the field's
     *                            group / type / tag constraint
     * @throws ConflictResponse   when a single-value slot is already taken
     */
    public EventField toggleSelfRegistration(int eventId, int fieldId, int memberId) {
        var field = repository.findById(fieldId).orElseThrow(NotFoundResponse::new);
        if (field.eventId() != eventId) {
            throw new NotFoundResponse();
        }
        if (!field.fieldType().isMemberField()) {
            throw new BadRequestResponse("Field is not a member field");
        }
        if (!field.config().selfRegistration()) {
            throw new BadRequestResponse("Self-registration is not enabled for this field");
        }

        var member = memberRepository.findById(memberId).orElseThrow(() -> new BadRequestResponse("Member not found"));
        ensureEligible(field, member);

        String newValue;
        if (field.fieldType().isMemberListField()) {
            var ids = MemberFieldValue.parseIds(field.value());
            if (ids.contains(memberId)) {
                ids.removeIf(id -> id == memberId);
            } else {
                ids.add(memberId);
            }
            newValue = MemberFieldValue.formatList(ids);
        } else {
            var ids = MemberFieldValue.parseIds(field.value());
            if (ids.isEmpty()) {
                newValue = MemberFieldValue.formatSingle(memberId);
            } else if (ids.getFirst() == memberId) {
                newValue = "";
            } else {
                throw new ConflictResponse("Slot is already taken");
            }
        }

        repository.updateValue(fieldId, newValue);
        log.info("Member {} toggled self-registration on event field {}", memberId, fieldId);
        return repository.findById(fieldId).orElseThrow(NotFoundResponse::new);
    }

    private void ensureEligible(EventField field, StationMember member) {
        var config = field.config();
        switch (field.fieldType().constraint()) {
            case GROUP -> {
                if (config.groupId() == null) {
                    throw new BadRequestResponse("Field is missing its group reference");
                }
                boolean inGroup = groupRepository.findGroupsForMember(member.id()).stream()
                        .anyMatch(g -> g.id() == config.groupId());
                if (!inGroup) {
                    throw new ForbiddenResponse("Member is not in the required group");
                }
            }
            case USER_TYPE -> {
                if (config.userType() == null) {
                    throw new BadRequestResponse("Field is missing its user type reference");
                }
                if (member.userType() != config.userType()) {
                    throw new ForbiddenResponse("Member does not have the required user type");
                }
            }
            case TAG -> {
                if (config.tagId() == null) {
                    throw new BadRequestResponse("Field is missing its tag reference");
                }
                boolean hasTag =
                        tagService.findTagsForMember(member.id()).stream().anyMatch(t -> t.id() == config.tagId());
                if (!hasTag) {
                    throw new ForbiddenResponse("Member does not have the required tag");
                }
            }
            case NONE -> {}
        }
    }
}
