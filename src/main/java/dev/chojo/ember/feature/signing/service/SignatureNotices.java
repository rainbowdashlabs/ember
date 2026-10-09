/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.documents.entity.Document;
import dev.chojo.ember.feature.documents.repository.DocumentRepository;
import dev.chojo.ember.feature.mail.entity.SignatureInvitation;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.mail.service.MailRecipientService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.LinkHome;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationData.NotificationLink;
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.notifications.service.NotificationText;
import dev.chojo.ember.feature.notifications.service.Notifier;
import dev.chojo.ember.feature.signing.entity.FieldRole;
import dev.chojo.ember.feature.signing.entity.RequestedSignature;
import dev.chojo.ember.feature.signing.entity.SignatureRequest;
import dev.chojo.ember.feature.signing.entity.SignatureWithdrawal;
import dev.chojo.ember.feature.signing.entity.SignerCapacity;
import dev.chojo.ember.feature.signing.entity.StoredEvidence;
import dev.chojo.ember.feature.station.entity.StationFormat;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Tells people about signatures: that a document waits for theirs, that it still does, and that it was given.
 *
 * <p><b>Who is asked.</b> A field asks the people who may sign it, exactly as the open tasks list it: the
 * member and their guardians for a member's own field (a member without a login signs through a guardian's
 * account), the guardian at a place for that place's field, every guardian for a field any of them may sign,
 * the issuer for the issuer's. Each of them is notified in the app, once per field, linked to the field's
 * signing screen. Whoever has left the station is asked nothing, nor is a guardian who no longer looks after
 * the member, and nobody is asked about a document whose member has left.
 *
 * <p><b>By mail.</b> A signature asked for is something to act on, so the request and every reminder also go
 * out by mail through the instance-wide relay, whatever the reader chose for their digest. Mail goes to the
 * account that signs: the member's own for a member with a login, the guardians' for a member without one or
 * for a guardian field. Whoever cannot be written to directly is reached through their guardians
 * ({@link MailRecipientService}). One address gets one mail per document, however many of its fields it is
 * asked for, linked to the first of them. The mail is written in the station's language.
 *
 * <p><b>Given.</b> Every signature is told in the app to the member who asked for the signatures, unless they
 * gave it themselves; their digest mails it where they chose so.
 *
 * <p><b>Withdrawn.</b> A withdrawn agreement is told in the app to whoever runs the appointment that asked for
 * it, or to whoever asked for the signatures, except to whoever withdrew it. One a partner station reports
 * withdrawn by its member is told to whoever runs the appointment here, naming the member only where the
 * partner shares the name.
 *
 * <p><b>Settled.</b> Once a field no longer waits, by a signature, on paper, waived or withdrawn, the unread
 * requests and reminders for it are taken back, since there is nothing left to do about them.
 *
 * <p>Telling people runs after the change it is about has been stored and never undoes it: a failure here is
 * logged, and the signature, the request or the reminder count stands.
 */
@Singleton
public class SignatureNotices {
    private static final Logger log = LoggerFactory.getLogger(SignatureNotices.class);

    private final Notifier notifier;
    private final NotificationText text;
    private final EmailService email;
    private final MailRecipientService mailRecipients;
    private final StationRepository stations;
    private final StationMemberRepository members;
    private final DocumentRepository documents;

    @Inject
    public SignatureNotices(
            Notifier notifier,
            NotificationText text,
            EmailService email,
            MailRecipientService mailRecipients,
            StationRepository stations,
            StationMemberRepository members,
            DocumentRepository documents) {
        this.notifier = notifier;
        this.text = text;
        this.email = email;
        this.mailRecipients = mailRecipients;
        this.stations = stations;
        this.members = members;
        this.documents = documents;
    }

    /**
     * Asks everybody a new request names for their signatures.
     *
     * @param request the request
     * @param fields  its fields, as stored
     */
    public void asked(SignatureRequest request, List<RequestedSignature> fields) {
        tell(request, fields, Kind.REQUEST);
    }

    /**
     * Reminds everybody of the fields of one request that still wait for them.
     *
     * @param request the request
     * @param fields  the fields due a reminder
     */
    public void reminded(SignatureRequest request, List<RequestedSignature> fields) {
        tell(request, fields, Kind.REMINDER);
    }

    /**
     * Tells whoever asked for the signatures that a field was signed.
     *
     * @param request  the request
     * @param evidence the act
     */
    public void signed(SignatureRequest request, StoredEvidence evidence) {
        Integer askedBy = request.createdBy();
        if (askedBy == null) return;
        try {
            var act = evidence.evidence().act();
            var params = new NotificationParams.DocumentSigned(
                    titleOf(request),
                    act.signerName(),
                    signedByTheMember(request, evidence) ? null : request.memberName());
            Integer memberId = request.memberId();
            var link = memberId == null ? NotificationLinks.ownDocuments() : NotificationLinks.member(memberId);
            notifier.notify(
                    StationAudience.member(askedBy).except(evidence.accountMemberId()),
                    NotificationType.DOCUMENT_SIGNED,
                    NotificationData.of(params, link),
                    Delivery.EVERY_TIME);
        } catch (RuntimeException e) {
            log.warn(
                    "Could not tell that field {} of signing request {} was signed",
                    evidence.fieldId(),
                    request.uid(),
                    e);
        }
    }

    /**
     * Tells the side that asked for an agreement that it was withdrawn: whoever runs the appointment that
     * asked for it, else whoever asked for the signatures. Whoever withdrew it is not told.
     *
     * @param request    the request whose agreement was withdrawn
     * @param withdrawal the withdrawal
     * @param eventId    the appointment that asked for the document, or null where none did
     */
    public void withdrawn(SignatureRequest request, SignatureWithdrawal withdrawal, @Nullable Integer eventId) {
        try {
            var params = new NotificationParams.SignatureWithdrawn(
                    titleOf(request), withdrawal.withdrawnByName(), request.memberName());
            StationAudience audience;
            NotificationLink link;
            if (eventId != null) {
                audience = StationAudience.holders(request.stationId(), StationPermission.EVENT_MANAGER);
                link = NotificationLinks.event(eventId);
            } else {
                Integer askedBy = request.createdBy();
                if (askedBy == null) return;
                audience = StationAudience.member(askedBy);
                Integer memberId = request.memberId();
                link = memberId == null ? NotificationLinks.ownDocuments() : NotificationLinks.member(memberId);
            }
            notifier.notify(
                    audience.except(withdrawal.withdrawnBy()),
                    NotificationType.SIGNATURE_WITHDRAWN,
                    NotificationData.of(params, link),
                    Delivery.EVERY_TIME);
        } catch (RuntimeException e) {
            log.warn("Could not tell that the agreement of signing request {} was withdrawn", request.uid(), e);
        }
    }

    /**
     * Tells whoever runs an appointment that a partner station reported a signed agreement of one of its
     * members withdrawn there.
     *
     * @param stationId     the station holding the appointment
     * @param eventId       the appointment
     * @param documentTitle what the document is called
     * @param stationName   the partner station's name
     * @param memberName    the name the partner shares for the member, or null where it shares none
     */
    public void partnerWithdrawn(
            int stationId, int eventId, String documentTitle, String stationName, @Nullable String memberName) {
        try {
            notifier.notify(
                    StationAudience.holders(stationId, StationPermission.EVENT_MANAGER),
                    NotificationType.PARTNER_SIGNATURE_WITHDRAWN,
                    NotificationData.of(
                            new NotificationParams.PartnerSignatureWithdrawn(documentTitle, stationName, memberName),
                            NotificationLinks.event(eventId)),
                    Delivery.EVERY_TIME);
        } catch (RuntimeException e) {
            log.warn(
                    "Could not tell that partner {} withdrew an agreement for appointment {}", stationName, eventId, e);
        }
    }

    /** Whether the member the document is about signed it, with their own account or through a guardian's. */
    private static boolean signedByTheMember(SignatureRequest request, StoredEvidence evidence) {
        var signer = evidence.evidence().act().signer();
        if (signer.capacity() == SignerCapacity.MEMBER_THROUGH_ACCOUNT) return true;
        Integer memberId = request.memberId();
        return signer.capacity() == SignerCapacity.ACCOUNT_HOLDER
                && memberId != null
                && memberId.equals(evidence.accountMemberId());
    }

    /**
     * Takes back the unread requests and reminders for fields that no longer wait for a signature.
     *
     * @param fieldIds the fields
     */
    public void settled(Collection<Integer> fieldIds) {
        for (int fieldId : fieldIds) {
            try {
                var link = NotificationLinks.signingField(fieldId);
                notifier.withdraw(NotificationType.SIGNATURE_REQUESTED, link);
                notifier.withdraw(NotificationType.SIGNATURE_REMINDER, link);
            } catch (RuntimeException e) {
                log.warn("Could not take back the notifications of signature field {}", fieldId, e);
            }
        }
    }

    private void tell(SignatureRequest request, List<RequestedSignature> fields, Kind kind) {
        try {
            var station = stations.findById(request.stationId()).orElse(null);
            if (station == null || !memberStays(request)) return;
            String title = titleOf(request);
            var params = new NotificationParams.SignatureRequested(title, request.memberName());
            var home = LinkHome.station(station.uid());
            var mails = new LinkedHashMap<String, Mail>();
            for (var field : fields) {
                var link = NotificationLinks.signingField(field.id());
                var data = NotificationData.of(params, link);
                audienceOf(field).ifPresent(audience -> notifier.notify(audience, kind.type, data, kind.delivery));
                String url = text.resolveLinkUrl(email.getBaseUrl(), home, link);
                for (int accountId : signingAccountsOf(field)) {
                    for (var recipient : mailRecipients.forAccount(accountId)) {
                        mails.putIfAbsent(recipient.email().toLowerCase(Locale.ROOT), new Mail(recipient, url));
                    }
                }
            }
            String locale = StationFormat.languageOf(station);
            for (var mail : mails.values()) {
                send(
                        kind,
                        mail,
                        new SignatureInvitation(station.name(), title, request.memberName(), mail.url),
                        locale);
            }
        } catch (RuntimeException e) {
            log.warn("Could not ask for the signatures of signing request {}", request.uid(), e);
        }
    }

    private void send(Kind kind, Mail mail, SignatureInvitation invitation, String locale) {
        var recipient = mail.recipient;
        switch (kind) {
            case REQUEST ->
                email.sendSignatureRequest(
                        recipient.email(), recipient.name(), recipient.guardian(), invitation, locale);
            case REMINDER ->
                email.sendSignatureReminder(
                        recipient.email(), recipient.name(), recipient.guardian(), invitation, locale);
        }
    }

    /** The members a field is shown to among their open tasks. */
    private Optional<StationAudience> audienceOf(RequestedSignature field) {
        Integer member = field.memberId();
        return switch (field.role()) {
            case PARTICIPANT -> signerOf(field).map(signer -> StationAudience.household(List.of(signer.id())));
            case GUARDIAN, ISSUER -> signerOf(field).map(signer -> StationAudience.member(signer.id()));
            case ANY_GUARDIAN -> Optional.ofNullable(member).map(id -> StationAudience.guardiansOf(List.of(id)));
        };
    }

    /** The accounts that would confirm a field's signature. */
    private List<Integer> signingAccountsOf(RequestedSignature field) {
        Integer member = field.memberId();
        return switch (field.role()) {
            case PARTICIPANT ->
                signerOf(field)
                        .map(signer -> field.capacity() == SignerCapacity.ACCOUNT_HOLDER
                                ? accountOf(signer)
                                : guardianAccounts(signer.id()))
                        .orElse(List.of());
            case GUARDIAN, ISSUER ->
                signerOf(field).map(SignatureNotices::accountOf).orElse(List.of());
            case ANY_GUARDIAN -> member == null ? List.of() : guardianAccounts(member);
        };
    }

    /** Whether the member the document is about is still at the station. */
    private boolean memberStays(SignatureRequest request) {
        Integer memberId = request.memberId();
        return memberId != null
                && members.findById(memberId).filter(member -> !member.former()).isPresent();
    }

    /**
     * The member a field names as its signer, while they may still sign it: still at the station, and for a
     * guardian's field still looking after the member the document is about.
     */
    private Optional<StationMember> signerOf(RequestedSignature field) {
        Integer signer = field.signerId();
        if (signer == null) return Optional.empty();
        if (field.role() == FieldRole.GUARDIAN) {
            Integer ward = field.memberId();
            if (ward == null) return Optional.empty();
            return members.findManagers(ward).stream()
                    .filter(guardian -> guardian.id() == signer)
                    .findFirst();
        }
        return members.findById(signer).filter(member -> !member.former());
    }

    private static List<Integer> accountOf(StationMember member) {
        return Optional.ofNullable(member.accountId()).stream().toList();
    }

    private List<Integer> guardianAccounts(int memberId) {
        return members.findManagers(memberId).stream()
                .map(StationMember::accountId)
                .filter(Objects::nonNull)
                .toList();
    }

    private String titleOf(SignatureRequest request) {
        Integer documentId = request.documentId();
        return Optional.ofNullable(documentId)
                .flatMap(documents::findById)
                .map(Document::title)
                .orElse(request.memberName());
    }

    /** A mail to one address, and the field it leads to. */
    private record Mail(MailRecipientService.Recipient recipient, String url) {}

    /** Whether a field is asked for the first time or again. */
    private enum Kind {
        REQUEST(NotificationType.SIGNATURE_REQUESTED, Delivery.EVERY_TIME),
        REMINDER(NotificationType.SIGNATURE_REMINDER, Delivery.ONCE_WHILE_UNREAD);

        private final NotificationType type;
        private final Delivery delivery;

        Kind(NotificationType type, Delivery delivery) {
            this.type = type;
            this.delivery = delivery;
        }
    }
}
