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
package com.github.joelcho.bsdiff;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public class QSufSortByteBufferImpl implements QSufSort {

    private final BufferAllocator allocator;

    public QSufSortByteBufferImpl(BufferAllocator allocator) {
        this.allocator = allocator;
    }

    @Override
    public IntBuffer sort(ByteBuffer buffer, int length) throws IOException {
        IntBuffer I = allocator.allocate((length + 1) * 4).asIntBuffer();
        IntBuffer V = allocator.allocate((length + 1) * 4).asIntBuffer();
        qsufsort(I, V, buffer, length);
        return I;
    }

    private static void qsufsort(IntBuffer I, IntBuffer V, ByteBuffer old, int oldsize) {
        int[] buckets = new int[256];
        int i, h, len;
        for (i = 0; i < oldsize; i++) buckets[Byte.toUnsignedInt(old.get(i))]++;
        for (i = 1; i < 256; i++) buckets[i] += buckets[i - 1];
        for (i = 255; i > 0; i--) buckets[i] = buckets[i - 1];
        buckets[0] = 0;

        for (i = 0; i < oldsize; i++) I.put(++buckets[Byte.toUnsignedInt(old.get(i))], i);
        I.put(0, oldsize);
        for (i = 0; i < oldsize; i++) V.put(i, buckets[Byte.toUnsignedInt(old.get(i))]);
        V.put(oldsize, 0);
        for (i = 1; i < 256; i++) if (buckets[i] == buckets[i - 1] + 1) I.put(buckets[i], -1);
        I.put(0, -1);

        for (h = 1; I.get(0) != -(oldsize + 1); h += h) {
            len = 0;
            for (i = 0; i < oldsize + 1; ) {
                if (I.get(i) < 0) {
                    len -= I.get(i);
                    i -= I.get(i);
                } else {
                    if (len != 0) I.put(i - len, -len);
                    len = V.get(I.get(i)) + 1 - i;
                    split(I, V, i, len, h);
                    i += len;
                    len = 0;
                }
            }
            if (len != 0) I.put(i - len, -len);
        }
        for (i = 0; i < oldsize + 1; i++) I.put(V.get(i), i);
    }

    private static void split(IntBuffer I, IntBuffer V, int start, int len, int h) {
        int i, j, k, x, tmp, jj, kk;

        if (len < 16) {
            for (k = start; k < start + len; k += j) {
                j = 1;
                x = V.get(I.get(k) + h);
                for (i = 1; k + i < start + len; i++) {
                    int y = V.get(I.get(k + i) + h);
                    if (y < x) {
                        x = y;
                        j = 0;
                    }
                    if (y == x) {
                        tmp = I.get(k + j);
                        I.put(k + j, I.get(k + i));
                        I.put(k + i, tmp);
                        j++;
                    }
                }
                for (i = 0; i < j; i++) V.put(I.get(k + i), k + j - 1);
                if (j == 1) I.put(k, -1);
            }
            return;
        }
        x = V.get(I.get(start + len / 2) + h);
        jj = 0;
        kk = 0;
        for (i = start; i < start + len; i++) {
            int y = V.get(I.get(i) + h);
            if (y < x) jj++;
            if (y == x) kk++;
        }
        jj += start;
        kk += jj;

        i = start;
        j = 0;
        k = 0;
        while (i < jj) {
            int y = V.get(I.get(i) + h);
            if (y < x) {
                i++;
            } else if (y == x) {
                tmp = I.get(i);
                I.put(i, I.get(jj + j));
                I.put(jj + j, tmp);
                j++;
            } else {
                tmp = I.get(i);
                I.put(i, I.get(kk + k));
                I.put(kk + k, tmp);
                k++;
            }
        }

        while (jj + j < kk) {
            if (V.get(I.get(jj + j) + h) == x) {
                j++;
            } else {
                tmp = I.get(jj + j);
                I.put(jj + j, I.get(kk + k));
                I.put(kk + k, tmp);
                k++;
            }
        }

        if (jj > start) split(I, V, start, jj - start, h);

        for (i = 0; i < kk - jj; i++) V.put(I.get(jj + i), kk - 1);
        if (jj == kk - 1) I.put(jj, -1);

        if (start + len > kk) split(I, V, kk, start + len - kk, h);
    }
}
