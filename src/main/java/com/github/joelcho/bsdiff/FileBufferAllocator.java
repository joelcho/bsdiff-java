/*-
 * Copyright 2021 joelcho
 * All rights reserved
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted providing that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
 * IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
// File created at: Friday, May 14, 2021
// File encoding  : UTF-8
// Line separator : LF
// Tab stop       : 4 spaces
// IDE            : IntelliJ IDEA community edition
package com.github.joelcho.bsdiff;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Map;

/**
 * BufferAllocator based on file
 *
 * @author Joel
 */
public class FileBufferAllocator implements BufferAllocator, Closeable {
    private final File tempDir;
    private final Map<File, RandomAccessFile> files = new HashMap<>();

    public FileBufferAllocator() throws IOException {
        this(System.getProperty("java.io.tmpdir"));
    }

    public FileBufferAllocator(String dir) throws IOException {
        File file = new File(dir);
        if (!(file.exists() && file.isDirectory())) {
            if (!file.mkdirs()) {
                throw new IOException("cannot create temp dir " + dir);
            }
        }
        this.tempDir = file;
    }

    public ByteBuffer allocate(String prefix, int size) throws IllegalArgumentException, IOException {
        if (size <= 0) {
            throw new IllegalArgumentException("invalid buffer size " + size);
        }
        final File tempFile = File.createTempFile(prefix, null, tempDir);
        RandomAccessFile raf = null;
        ByteBuffer buffer = null;
        Exception err = null;
        try {
            raf = new RandomAccessFile(tempFile, "rw");
            FileChannel channel = raf.getChannel();
            channel.truncate(size);
            buffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, size);
        } catch (Exception e) {
            err = e;
        }

        if (err != null) {
            if (raf != null) {
                raf.close();
            }
            final boolean delete = tempFile.delete();
            assert delete;
            throw new IOException("create buffer failed", err);
        }
        this.files.put(tempFile, raf);
        return buffer;
    }

    @Override
    public ByteBuffer allocate(int size) throws IllegalArgumentException, IOException {
        return this.allocate("filebuf", size);
    }

    @Override
    public void close() throws IOException {
        IOException err = null;
        for (Map.Entry<File, RandomAccessFile> entry : files.entrySet()) {
            try {
                entry.getValue().close();
            } catch (IOException e) {
                err = e;
            }
            final boolean delete = entry.getKey().delete();
            assert delete;
        }
        if (err != null) {
            throw err;
        }
    }
}
