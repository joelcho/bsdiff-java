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
// File created at: Thursday, May 20, 2021
// File encoding  : UTF-8
// Line separator : LF
// Tab stop       : 4 spaces
// IDE            : IntelliJ IDEA community edition
package com.github.joelcho.bsdiff;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * Seekable ByteArrayOutputStream
 *
 * @author Joel
 */
public class SeekableByteArrayOutputStream extends SeekableOutputStream {
    private byte[] buf;
    private int pos;
    private int len;
    private final float expandFactor;

    public SeekableByteArrayOutputStream() {
        this(8, 2);
    }

    public SeekableByteArrayOutputStream(int initialCapacity) {
        this(initialCapacity, 2);
    }

    public SeekableByteArrayOutputStream(int initialCapacity, float expandFactor) {
        this.buf = new byte[initialCapacity];
        this.expandFactor = expandFactor;
        this.pos = 0;
    }

    @Override
    public long position() throws IOException {
        return pos;
    }

    @Override
    public void seek(int position) throws IOException {
        if (position > len - 1 || position < 0) {
            throw new IndexOutOfBoundsException("" + position);
        }
        this.pos = position;
    }

    @Override
    public void write(byte[] b, int off, int n) throws IOException {
        ensureSpace(n);
        System.arraycopy(b, off, buf, pos, n);
        pos += n;
        if (pos >= len) {
            len = pos;
        }
    }

    @Override
    public void write(int b) throws IOException {
        ensureSpace(1);
        buf[pos] = (byte) b;
        pos++;
        if (pos > len) {
            len = pos;
        }
    }

    public void truncate(int newSize) {
        this.buf = Arrays.copyOf(this.buf, newSize);

        int m = newSize - 1;
        if (pos > m) {
            pos = m;
        }
        if (newSize < len) {
            len = newSize;
        }
    }

    public int size() {
        return len;
    }

    public int capacity() {
        return buf.length;
    }

    public byte[] toByteArray() {
        byte[] array = new byte[len];
        System.arraycopy(this.buf, 0, array, 0, len);
        return array;
    }

    public void writeTo(OutputStream out) throws IOException {
        out.write(buf, 0, len);
    }

    private void ensureSpace(int needed) {
        if (buf.length - pos > needed) {
            return;
        }
        int newCapacity = (int) (buf.length * expandFactor);
        while (newCapacity < (buf.length + needed)) {
            newCapacity *= expandFactor;
        }
        if (newCapacity > MAX_ARRAY_SIZE || newCapacity < 0) {
            throw new OutOfMemoryError();
        }
        this.buf = Arrays.copyOf(this.buf, newCapacity);
    }

    private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;
}
