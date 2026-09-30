/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mailimport.service;

import com.icegreen.greenmail.util.GreenMail;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;

import java.util.Properties;

/**
 * A second mail client on the same account, doing what people do to a shared mailbox while the import
 * is reading it.
 */
final class AnotherClient {

    private AnotherClient() {}

    /**
     * Deletes one message of the inbox and expunges it, over a connection of its own.
     *
     * @param server        the mail server
     * @param user          the account
     * @param password      its password
     * @param messageNumber the message's position in the inbox, counted from one
     */
    static void expunge(GreenMail server, String user, String password, int messageNumber) throws MessagingException {
        Store store = Session.getInstance(new Properties()).getStore("imap");
        store.connect("127.0.0.1", server.getImap().getPort(), user, password);
        try {
            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_WRITE);
            inbox.getMessage(messageNumber).setFlag(Flags.Flag.DELETED, true);
            inbox.close(true);
        } finally {
            store.close();
        }
    }
}
