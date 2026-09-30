/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend.smb;

import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.mserref.NtStatus;
import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2CreateOptions;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.mssmb2.SMBApiException;
import com.hierynomus.smbj.common.SMBRuntimeException;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import com.hierynomus.smbj.share.File;
import dev.chojo.ember.feature.storage.backend.tree.FileInfo;
import dev.chojo.ember.feature.storage.backend.tree.FileTree;
import dev.chojo.ember.feature.storage.backend.tree.OpenFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * One signed-in SMB session and its share as a {@link FileTree}. Tree paths are taken from the root of
 * the share and spelled with backslashes on the wire.
 *
 * <p>A file is replaced by renaming onto the target with the replace flag set, which the server does
 * in one request. smbj reports failures as unchecked exceptions; they leave here as
 * {@link IOException}s, and a name the server says is not there as absence.
 */
final class SmbFileTree implements FileTree {
    private static final Logger log = LoggerFactory.getLogger(SmbFileTree.class);
    private static final Set<NtStatus> MISSING = EnumSet.of(
            NtStatus.STATUS_OBJECT_NAME_NOT_FOUND, NtStatus.STATUS_OBJECT_PATH_NOT_FOUND, NtStatus.STATUS_NO_SUCH_FILE);

    private final Session session;
    private final DiskShare share;

    SmbFileTree(Session session, DiskShare share) {
        this.session = session;
        this.share = share;
    }

    private static String smb(String path) {
        return path.replace('/', '\\');
    }

    private static boolean isMissing(SMBRuntimeException e) {
        return e instanceof SMBApiException api && MISSING.contains(api.getStatus());
    }

    private static IOException failure(SMBRuntimeException e) {
        return new IOException(e.getMessage(), e);
    }

    @Override
    public OutputStream create(String path) throws IOException {
        try {
            File file = share.openFile(
                    smb(path),
                    EnumSet.of(AccessMask.GENERIC_WRITE, AccessMask.DELETE),
                    EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OVERWRITE_IF,
                    EnumSet.noneOf(SMB2CreateOptions.class));
            return new FilterOutputStream(file.getOutputStream()) {
                @Override
                public void write(byte[] b, int off, int len) throws IOException {
                    out.write(b, off, len);
                }

                @Override
                public void close() throws IOException {
                    try {
                        super.close();
                    } finally {
                        file.close();
                    }
                }
            };
        } catch (SMBRuntimeException e) {
            if (isMissing(e)) throw new NoSuchFileException(path);
            throw failure(e);
        }
    }

    @Override
    public Optional<OpenFile> open(String path) throws IOException {
        try {
            File file = openForRead(smb(path));
            long size = file.getFileInformation().getStandardInformation().getEndOfFile();
            InputStream body = new FilterInputStream(file.getInputStream()) {
                @Override
                public void close() throws IOException {
                    try {
                        super.close();
                    } finally {
                        file.close();
                    }
                }
            };
            return Optional.of(new OpenFile(body, size));
        } catch (SMBRuntimeException e) {
            if (isMissing(e)) return Optional.empty();
            throw failure(e);
        }
    }

    @Override
    public Optional<FileInfo> stat(String path) throws IOException {
        try {
            if (path.isEmpty()) return Optional.of(new FileInfo("", true, 0));
            var information = share.getFileInformation(smb(path)).getStandardInformation();
            return Optional.of(new FileInfo(nameOf(path), information.isDirectory(), information.getEndOfFile()));
        } catch (SMBRuntimeException e) {
            if (isMissing(e)) return Optional.empty();
            throw failure(e);
        }
    }

    @Override
    public void replace(String source, String target) throws IOException {
        try (File file = share.openFile(
                smb(source),
                EnumSet.of(AccessMask.DELETE, AccessMask.GENERIC_READ),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN,
                null)) {
            file.rename(smb(target), true);
        } catch (SMBRuntimeException e) {
            throw failure(e);
        }
    }

    @Override
    public boolean remove(String path) throws IOException {
        try {
            share.rm(smb(path));
            return true;
        } catch (SMBRuntimeException e) {
            if (isMissing(e)) return false;
            throw failure(e);
        }
    }

    @Override
    public void makeDirectories(String path) throws IOException {
        if (path.isEmpty()) return;
        try {
            if (share.folderExists(smb(path))) return;
            int slash = path.lastIndexOf('/');
            if (slash > 0) makeDirectories(path.substring(0, slash));
            share.mkdir(smb(path));
        } catch (SMBApiException e) {
            if (e.getStatus() != NtStatus.STATUS_OBJECT_NAME_COLLISION) throw failure(e);
        } catch (SMBRuntimeException e) {
            throw failure(e);
        }
    }

    @Override
    public List<FileInfo> list(String directory) throws IOException {
        try {
            var out = new ArrayList<FileInfo>();
            for (FileIdBothDirectoryInformation entry : share.list(smb(directory))) {
                String name = entry.getFileName();
                if (name.equals(".") || name.equals("..")) continue;
                boolean isDirectory =
                        (entry.getFileAttributes() & FileAttributes.FILE_ATTRIBUTE_DIRECTORY.getValue()) != 0;
                out.add(new FileInfo(name, isDirectory, isDirectory ? 0 : entry.getEndOfFile()));
            }
            return out;
        } catch (SMBRuntimeException e) {
            if (isMissing(e)) return List.of();
            throw failure(e);
        }
    }

    @Override
    public boolean removeDirectory(String path) {
        try {
            share.rmdir(smb(path), false);
            return true;
        } catch (SMBRuntimeException e) {
            return false;
        }
    }

    @Override
    public boolean isUsable() {
        return share.isConnected() && session.getConnection().isConnected();
    }

    @Override
    public void close() {
        try {
            share.close();
        } catch (IOException | SMBRuntimeException e) {
            log.debug("Closing an SMB share failed", e);
        }
        try {
            session.close();
        } catch (IOException | SMBRuntimeException e) {
            log.debug("Closing an SMB session failed", e);
        }
        try {
            session.getConnection().close();
        } catch (IOException | SMBRuntimeException e) {
            log.debug("Releasing an SMB connection failed", e);
        }
    }

    private File openForRead(String path) {
        return share.openFile(
                path,
                EnumSet.of(AccessMask.GENERIC_READ),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN,
                null);
    }

    private static String nameOf(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }
}
