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
// File created at: Thursday, May 13, 2021
// File encoding  : UTF-8
// Line separator : LF
// Tab stop       : 4 spaces
// IDE            : IntelliJ IDEA community edition
package com.github.joelcho.bsdiff;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * BSDiff v4.3
 * <p>
 * original <a href="http://www.daemonology.net/bsdiff/">bsdiff</a>
 *
 * @author Joel
 */
public class BSDiff {
    static final byte[] VERSION = "BSDIFF40".getBytes();
    static final int HEADER_SIZE = 32;

    public static void diff(ByteBuffer old, ByteBuffer new0, SeekableOutputStream out, BufferAllocator ba) throws IOException {
        diff(old, new0, out, ba, new QSufSortByteBufferImpl(ba));
    }

    /**
     * Header is
     * 0	8	"BSDIFF40"
     * 8	8	length of bzip2ed ctrl block
     * 16	8	length of bzip2ed diff block
     * 24	8	length of new file
     * <p>
     * File is
     * 0	32	Header
     * 32	??	Bzip2ed ctrl block
     * ??	??	Bzip2ed diff block
     * ??	??	Bzip2ed extra block
     *
     * @param old      old file
     * @param new0     new file
     * @param out      diff output
     * @param ba       temp buffer creator
     * @param qSufSort the QSufSort implementation
     * @throws IOException If some other I/O error occurs
     */
    public static void diff(ByteBuffer old, ByteBuffer new0, SeekableOutputStream out, BufferAllocator ba, QSufSort qSufSort) throws IOException {
        final int oldsize = old.limit();
        int newsize = new0.limit();
        IntBuffer I = qSufSort.sort(old, oldsize);

        out.write(new byte[HEADER_SIZE]); // header placeholder
        out.flush();
        BZip2CompressorOutputStream bzOut = new BZip2CompressorOutputStream(out, 9);

        int scan = 0, len = 0;
        int lastscan = 0, lastpos = 0, lastoffset = 0;
        int oldscore, scsc;
        int s, Sf, lenf, Sb, lenb;
        int overlap, Ss, lens;
        int i;
        int dblen = 0, eblen = 0;

        ByteBuffer db = ba.allocate((oldsize + 1) * 4);
        ByteBuffer eb = ba.allocate((oldsize + 1) * 4);

        AtomicInteger pos = new AtomicInteger(0);
        while (scan < newsize) {
            oldscore = 0;

            for (scsc = scan += len; scan < newsize; scan++) {
                len = search(I, old, oldsize, subRef(new0, scan), newsize - scan,
                        0, oldsize, pos);

                for (; scsc < scan + len; scsc++)
                    if ((scsc + lastoffset < oldsize) &&
                            (old.get(scsc + lastoffset) == new0.get(scsc)))
                        oldscore++;

                if (((len == oldscore) && (len != 0)) ||
                        (len > oldscore + 8)) break;

                if ((scan + lastoffset < oldsize) &&
                        (old.get(scan + lastoffset) == new0.get(scan)))
                    oldscore--;
            }

            if ((len != oldscore) || (scan == newsize)) {
                s = 0;
                Sf = 0;
                lenf = 0;
                for (i = 0; (lastscan + i < scan) && (lastpos + i < oldsize); ) {
                    if (old.get(lastpos + i) == new0.get(lastscan + i)) s++;
                    i++;
                    if (s * 2 - i > Sf * 2 - lenf) {
                        Sf = s;
                        lenf = i;
                    }
                }

                lenb = 0;
                if (scan < newsize) {
                    s = 0;
                    Sb = 0;
                    for (i = 1; (scan >= lastscan + i) && (pos.get() >= i); i++) {
                        if (old.get(pos.get() - i) == new0.get(scan - i)) s++;
                        if (s * 2 - i > Sb * 2 - lenb) {
                            Sb = s;
                            lenb = i;
                        }
                    }
                }

                if (lastscan + lenf > scan - lenb) {
                    overlap = (lastscan + lenf) - (scan - lenb);
                    s = 0;
                    Ss = 0;
                    lens = 0;
                    for (i = 0; i < overlap; i++) {
                        if (new0.get(lastscan + lenf - overlap + i) ==
                                old.get(lastpos + lenf - overlap + i)) s++;
                        if (new0.get(scan - lenb + i) ==
                                old.get(pos.get() - lenb + i)) s--;
                        if (s > Ss) {
                            Ss = s;
                            lens = i + 1;
                        }
                    }

                    lenf += lens - overlap;
                    lenb -= lens;
                }

                for (i = 0; i < lenf; i++)
                    db.put(dblen + i, (byte) (new0.get(lastscan + i) - old.get(lastpos + i)));
                for (i = 0; i < (scan - lenb) - (lastscan + lenf); i++)
                    eb.put(eblen + i, new0.get(lastscan + lenf + i));

                dblen += lenf;
                eblen += (scan - lenb) - (lastscan + lenf);

                bzOut.write(offtout(lenf));
                bzOut.write(offtout((scan - lenb) - (lastscan + lenf)));
                bzOut.write(offtout((pos.get() - lenb) - (lastpos + lenf)));

                lastscan = scan - lenb;
                lastpos = pos.get() - lenb;
                lastoffset = pos.get() - scan;
            }
        }
        bzOut.flush();
        bzOut.finish();

        // Compute size of compressed ctrl data
        long ctrlDataLen = out.position() - HEADER_SIZE;

        // Write compressed diff data
        bzOut = new BZip2CompressorOutputStream(out, 9);
        writeByteBufferTo(db, dblen, bzOut);
        bzOut.flush();
        bzOut.finish();

        // Compute size of compressed diff data
        long diffDataLen = out.position() - HEADER_SIZE - ctrlDataLen;

        // Write compressed extra data
        bzOut = new BZip2CompressorOutputStream(out, 9);
        writeByteBufferTo(eb, eblen, bzOut);
        bzOut.flush();
        bzOut.finish();

        // fill header
        out.seek(0);
        out.write(VERSION);
        out.write(offtout(ctrlDataLen));
        out.write(offtout(diffDataLen));
        out.write(offtout(newsize));
        out.flush();
    }

    private static byte[] offtout(long v) {
        if (v < 0) {
            byte[] arr = ByteBuffer.allocate(Long.BYTES).order(ByteOrder.LITTLE_ENDIAN).putLong(-v).array();
            arr[7] |= 0x80;
            return arr;
        }
        return ByteBuffer.allocate(Long.BYTES).order(ByteOrder.LITTLE_ENDIAN).putLong(v).array();
    }

    private static void writeByteBufferTo(ByteBuffer buffer, int bufSize, OutputStream out) throws IOException {
        final int tempBuferSize = 10240;
        byte[] temp = new byte[tempBuferSize];
        while (bufSize > tempBuferSize) {
            buffer.get(temp);
            out.write(temp);
            bufSize -= tempBuferSize;
        }
        if (bufSize > 0) {
            temp = new byte[bufSize];
            buffer.get(temp);
            out.write(temp);
        }
    }

    static ByteBuffer subRef(ByteBuffer buffer, int begin) {
        int position = buffer.position();
        buffer.position(begin);
        final ByteBuffer slice = buffer.slice();
        buffer.position(position);
        return slice;
    }

    private static int matchlen(ByteBuffer oldBuf, int oldsize, ByteBuffer newBuf, int newsize) {
        int min = Math.min(oldsize, newsize);
        for (int i = 0; i < min; i++) {
            if (oldBuf.get(i) != newBuf.get(i)) {
                return i;
            }
        }
        return min;
    }

    private static int search(IntBuffer I, ByteBuffer old, int oldsize,
                              ByteBuffer new0, int newsize, int st, int en, AtomicInteger pos) {
        int x, y;
        if (en - st < 2) {
            x = matchlen(subRef(old, I.get(st)), oldsize - I.get(st), new0, newsize);
            y = matchlen(subRef(old, I.get(en)), oldsize - I.get(en), new0, newsize);

            if (x > y) {
                pos.set(I.get(st));
                return x;
            } else {
                pos.set(I.get(en));
                return y;
            }
        }

        x = st + (en - st) / 2;
        if (memcmp(subRef(old, I.get(x)), new0, Math.min(oldsize - I.get(x), newsize)) < 0) {
            return search(I, old, oldsize, new0, newsize, x, en, pos);
        } else {
            return search(I, old, oldsize, new0, newsize, st, x, pos);
        }
    }

    // https://opensource.apple.com/source/tcl/tcl-3.1/tcl/compat/memcmp.c
    private static int memcmp(ByteBuffer a, ByteBuffer b, int len) {
        for (int i = 0; i < len; i++) {
            int x = Byte.toUnsignedInt(a.get(i));
            int y = Byte.toUnsignedInt(b.get(i));
            if (x != y) {
                return x - y;
            }
        }
        return 0;
    }
}
