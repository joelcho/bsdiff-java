/*-
 * Copyright 2021 joelcho
 * Copyright 2003-2005 Colin Percival
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
// File created at: Wednesday, May 19, 2021
// File encoding  : UTF-8
// Line separator : LF
// Tab stop       : 4 spaces
// IDE            : IntelliJ IDEA community edition
package com.github.joelcho.bsdiff;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * BSPatch v4.3
 * <p>
 * original <a href="http://www.daemonology.net/bsdiff/">bsdiff</a>
 *
 * @author Joel
 */
public class BSPatch {

    /**
     * Parse new binary size.
     *
     * @param pathBuf patch buffer
     * @return the size of new binary file.
     * @throws IOException If some other I/O error occurs
     */
    public static int parseNewSize(ByteBuffer pathBuf) throws IOException {
        Object[] tuple = parseHeader(pathBuf);
        return ((Long) tuple[3]).intValue();
    }

    /**
     * @param old     the existing
     * @param new0    the new
     * @param pathBuf patch buffer
     * @throws IOException If some other I/O error occurs
     */
    public static void patch(ByteBuffer old, ByteBuffer new0, ByteBuffer pathBuf) throws IOException {
        final int oldsize = old.limit();

        final Object[] header = parseHeader(pathBuf);
        final long bzctrllen = (Long) header[1];
        final long bzdatalen = (Long) header[2];
        final long newsize = (Long) header[3];
        if (bzctrllen < 0 || bzdatalen < 0 | newsize < 0) {
            corruptPatch();
        }
        if (newsize > new0.limit()) {
            throw new IOException("short new buffer, require at least " + newsize);
        }
        int off = BSDiff.HEADER_SIZE;
        BZip2CompressorInputStream cpfbz2 = mkbzi(pathBuf, off, bzctrllen);
        off += bzctrllen;
        BZip2CompressorInputStream dpfbz2 = mkbzi(pathBuf, off, bzdatalen);
        off += bzdatalen;
        BZip2CompressorInputStream epfbz2 = mkbzi(pathBuf, off, pathBuf.limit() - off);

        int oldpos = 0, newpos = 0;
        int i;
        int lenread;
        final byte[] buf = new byte[8];
        final int[] ctrl = new int[3];
        while (newpos < newsize) {
            // read control data
            for (i = 0; i <= 2; i++) {
                lenread = cpfbz2.read(buf);
                if (lenread != 8) {
                    corruptPatch();
                }
                ctrl[i] = (int) offtin(buf, 0);
            }

            // sanity-check
            if (newpos + ctrl[0] > newsize) {
                corruptPatch();
            }

            // read diff string
            readToByteBuffer(dpfbz2, BSDiff.subRef(new0, newpos), ctrl[0]);

            // add old data to diff string
            for (i = 0; i < ctrl[0]; i++) {
                if ((oldpos + i >= 0) && (oldpos + i < oldsize)) {
                    int pos = newpos + i;
                    byte b = new0.get(pos);
                    b += old.get(oldpos + i);
                    new0.put(pos, b);
                }
            }

            // adjust pointers
            newpos += ctrl[0];
            oldpos += ctrl[0];

            // sanity-check
            if (newpos + ctrl[1] > newsize) {
                corruptPatch();
            }

            // read extra string
            readToByteBuffer(epfbz2, BSDiff.subRef(new0, newpos), ctrl[1]);

            // adjust pointers
            newpos += ctrl[1];
            oldpos += ctrl[2];
        }
    }

    // Read len bytes from in to target
    private static void readToByteBuffer(InputStream in, ByteBuffer target, int len) throws IOException {
        final int tempBuferSize = 10240;
        int nread = 0;
        byte[] buf;
        if (len > tempBuferSize) {
            buf = new byte[tempBuferSize];
            int s = len / tempBuferSize;
            for (int i = 0; i < s; i++) {
                int n = in.read(buf);
                if (n != tempBuferSize) {
                    throw new IOException("short read");
                }
                target.put(buf);
                nread += n;
            }
        }
        if (nread < len) {
            buf = new byte[len - nread];
            int n = in.read(buf);
            if (n != buf.length) {
                throw new IOException("short read");
            }
            target.put(buf);
        }
    }

    // corruptPatch Throws an IOException
    private static void corruptPatch() throws IOException {
        throw new IOException("corrupt patch");
    }

    private static long offtin(byte[] buf, int off) {
        assert buf.length > off + 7;
        long v = ByteBuffer.wrap(buf, off, 8).order(ByteOrder.LITTLE_ENDIAN).getLong();
        return (buf[off + 7] & 0x80) == 0 ? v : -v;
    }

    // [0](string) = version
    // [1](Long) = bzctrllen
    // [2](Long) = bzdatalen
    // [3](Long) = newsize
    private static Object[] parseHeader(ByteBuffer buffer) throws IOException {
        int pos = buffer.position();
        byte[] header = new byte[BSDiff.HEADER_SIZE];
        buffer.get(header);
        buffer.position(pos);
        for (int i = 0; i < BSDiff.VERSION.length; i++) {
            if (BSDiff.VERSION[i] != header[i]) {
                corruptPatch();
            }
        }
        long bzctrllen = offtin(header, 8);
        long bzdatalen = offtin(header, 16);
        long newsize = offtin(header, 24);
        if (bzctrllen < 0 || bzdatalen < 0 | newsize < 0) {
            corruptPatch();
        }
        return new Object[]{new String(header, 0, BSDiff.HEADER_SIZE), bzctrllen, bzdatalen, newsize};
    }

    // Make bzip2 input stream from buffer by given range
    private static BZip2CompressorInputStream mkbzi(ByteBuffer buf, long off, long len) throws IOException {
        int pos = buf.position();
        //int limit = buf.limit();
        buf.position((int) off);
        //buf.limit((int) (off + len));
        ByteBuffer slice = buf.slice();
        //buf.limit(limit);
        buf.position(pos);
        InputStream wrap = new WarppedByteBufferInputStream(slice);
        return new BZip2CompressorInputStream(wrap, true);
    }

    // An InputStream that wrapped ByteBuffer
    private static class WarppedByteBufferInputStream extends InputStream {
        final ByteBuffer bf;

        WarppedByteBufferInputStream(ByteBuffer buffer) {
            bf = buffer;
        }

        @Override
        public int read(byte[] b) throws IOException {
            int remain = bf.remaining();
            int lenb = b.length;
            if (remain >= lenb) {
                bf.get(b);
                return b.length;
            } else if (remain > 0) {
                byte[] tmp = new byte[remain];
                bf.get(tmp);
                return remain;
            }
            return -1;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int remain = bf.remaining();
            int nread = Math.min(remain, len);
            if (nread > 0) {
                bf.get(b, off, nread);
                return nread;
            }
            return -1;
        }

        @Override
        public int read() throws IOException {
            if (bf.remaining() > 0) {
                return bf.get() & 0xFF;
            }
            return -1;
        }
    }
}
