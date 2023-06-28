package com.github.joelcho.bsdiff;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/**
 * QSufSort algorithm.
 * <p>
 * {@link QSufSortByteArrayImpl} is a faster implementation, but it only uses JVM memory.
 * {@link QSufSortByteBufferImpl} is a slower implementation, it uses allocated memory by an {@link BufferAllocator}.
 * <p>
 * For large files (which size bigger than RAM) {@link QSufSortByteBufferImpl} is better,
 * otherwise {@link QSufSortByteArrayImpl} is best.
 *
 * @see QSufSortByteArrayImpl
 * @see QSufSortByteBufferImpl
 */
public interface QSufSort {
    IntBuffer sort(ByteBuffer buffer, int length) throws IOException;
}
