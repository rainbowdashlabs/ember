/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend.sftp;

import dev.chojo.ember.feature.storage.backend.tree.FileInfo;
import dev.chojo.ember.feature.storage.backend.tree.FileTree;
import dev.chojo.ember.feature.storage.backend.tree.OpenFile;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.extensions.openssh.OpenSSHPosixRenameExtension;
import org.apache.sshd.sftp.common.SftpConstants;
import org.apache.sshd.sftp.common.SftpException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

/**
 * One SFTP channel as a {@link FileTree}. Tree paths are taken from the server's root, which for a
 * chrooted account is its home.
 *
 * <p>A file is replaced through {@code posix-rename@openssh.com} where the server offers it, which
 * OpenSSH does and which moves onto an existing file in one atomic step. Elsewhere the target is
 * removed first and the file renamed after, leaving a moment in which neither exists.
 */
final class SftpFileTree implements FileTree {
    private static final Logger log = LoggerFactory.getLogger(SftpFileTree.class);
    private static final EnumSet<SftpClient.OpenMode> WRITE =
            EnumSet.of(SftpClient.OpenMode.Create, SftpClient.OpenMode.Write, SftpClient.OpenMode.Truncate);

    private final SftpClient sftp;
    private final OpenSSHPosixRenameExtension posixRename;

    SftpFileTree(SftpClient sftp) {
        this.sftp = sftp;
        var extension = sftp.getExtension(OpenSSHPosixRenameExtension.class);
        this.posixRename = extension != null && extension.isSupported() ? extension : null;
    }

    private static String absolute(String path) {
        return path.isEmpty() ? "/" : "/" + path;
    }

    private static boolean isMissing(SftpException e) {
        return e.getStatus() == SftpConstants.SSH_FX_NO_SUCH_FILE || e.getStatus() == SftpConstants.SSH_FX_NO_SUCH_PATH;
    }

    @Override
    public OutputStream create(String path) throws IOException {
        try {
            return sftp.write(absolute(path), WRITE);
        } catch (SftpException e) {
            if (isMissing(e)) throw new NoSuchFileException(path);
            throw e;
        }
    }

    @Override
    public Optional<OpenFile> open(String path) throws IOException {
        var info = stat(path);
        if (info.isEmpty() || info.get().directory()) return Optional.empty();
        try {
            return Optional.of(
                    new OpenFile(sftp.read(absolute(path)), info.get().size()));
        } catch (SftpException e) {
            if (isMissing(e)) return Optional.empty();
            throw e;
        }
    }

    @Override
    public Optional<FileInfo> stat(String path) throws IOException {
        try {
            var attributes = sftp.stat(absolute(path));
            return Optional.of(new FileInfo(nameOf(path), attributes.isDirectory(), attributes.getSize()));
        } catch (SftpException e) {
            if (isMissing(e)) return Optional.empty();
            throw e;
        }
    }

    @Override
    public void replace(String source, String target) throws IOException {
        if (posixRename != null) {
            posixRename.posixRename(absolute(source), absolute(target));
            return;
        }
        remove(target);
        sftp.rename(absolute(source), absolute(target));
    }

    @Override
    public boolean remove(String path) throws IOException {
        try {
            sftp.remove(absolute(path));
            return true;
        } catch (SftpException e) {
            if (isMissing(e)) return false;
            throw e;
        }
    }

    @Override
    public void makeDirectories(String path) throws IOException {
        if (path.isEmpty() || stat(path).isPresent()) return;
        int slash = path.lastIndexOf('/');
        if (slash > 0) makeDirectories(path.substring(0, slash));
        try {
            sftp.mkdir(absolute(path));
        } catch (SftpException e) {
            if (stat(path).filter(FileInfo::directory).isEmpty()) throw e;
        }
    }

    @Override
    public List<FileInfo> list(String directory) throws IOException {
        var out = new ArrayList<FileInfo>();
        try {
            for (SftpClient.DirEntry entry : sftp.readDir(absolute(directory))) {
                String name = entry.getFilename();
                if (name.equals(".") || name.equals("..")) continue;
                var attributes = entry.getAttributes();
                out.add(new FileInfo(
                        name, attributes.isDirectory(), attributes.isDirectory() ? 0 : attributes.getSize()));
            }
        } catch (SftpException e) {
            if (isMissing(e)) return List.of();
            throw e;
        } catch (UncheckedIOException e) {
            if (e.getCause() instanceof SftpException sftpFailure && isMissing(sftpFailure)) return List.of();
            throw e.getCause();
        }
        return out;
    }

    @Override
    public boolean removeDirectory(String path) {
        try {
            sftp.rmdir(absolute(path));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public boolean isUsable() {
        return sftp.isOpen() && sftp.getClientSession().isOpen();
    }

    @Override
    public void close() {
        try {
            sftp.close();
        } catch (IOException e) {
            log.debug("Closing an SFTP channel failed", e);
        }
    }

    private static String nameOf(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }
}
