// File created at: Thursday, May 20, 2021
// File encoding  : UTF-8
// Line separator : LF
// Tab stop       : 4 spaces
// IDE            : IntelliJ IDEA community edition
package com.github.joelcho.bsdiff;

import org.junit.Test;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * @author Joel
 */
public class Example {

    @Test
    public void fileDiff() throws IOException {
        final String oldFilePath = "/path/to/file.old";
        final String newFilePath = "/path/to/file.new";
        final String patchFilePath = "/path/to/file.old.new.path";

        try (FileInputStream ofis = new FileInputStream(oldFilePath)) {
            final ByteBuffer old =
                    ofis.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, ofis.available());
            try (FileInputStream nfis = new FileInputStream(newFilePath)) {
                final ByteBuffer new0 =
                        nfis.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, nfis.available());
                try (FileOutputStream pfos = new FileOutputStream(patchFilePath)) {
                    BufferAllocator bufferAllocator = ByteBuffer::allocate;
                    // or
                    // BufferAllocator bufferAllocator = new FileBufferAllocator();
                    BSDiff.diff(old, new0, new SeekableFileOutputStream(pfos), bufferAllocator);
                }
            }
        }
    }

    @Test
    public void filePatch() throws IOException {
        final String oldFilePath = "/path/to/file.old";
        final String newFilePath = "/path/to/file.new";
        final String patchFilePath = "/path/to/file.old.new.path";

        try (FileInputStream pfis = new FileInputStream(patchFilePath)) {
            final ByteBuffer pbuf = pfis.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, pfis.available());
            int newFileSize = BSPatch.parseNewSize(pbuf);
            try (RandomAccessFile newFile = new RandomAccessFile(newFilePath, "rw")) {
                newFile.getChannel().truncate(newFileSize);
                // Note: FileOutputStream channel does not support mapping immediately after truncation
                final ByteBuffer new0 = newFile.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, newFileSize);
                try (FileInputStream ofis = new FileInputStream(oldFilePath)) {
                    final ByteBuffer old =
                            ofis.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, ofis.available());
                    BSPatch.patch(old, new0, pbuf);
                }
            }
        }
    }

    @Test
    public void memoryDiff() throws IOException {
        byte[] oldMem = null;
        byte[] newMem = null;

        ByteBuffer old = ByteBuffer.wrap(oldMem);
        ByteBuffer new0 = ByteBuffer.wrap(newMem);

        SeekableByteArrayOutputStream sbos = new SeekableByteArrayOutputStream();
        BSDiff.diff(old, new0, sbos, ByteBuffer::allocate);
    }

    @Test
    public void memoryPatch() throws Exception {
        byte[] oldMem = null;
        byte[] patchMem = null;
        byte[] newMem;

        ByteBuffer old = ByteBuffer.wrap(oldMem);
        ByteBuffer p = ByteBuffer.wrap(patchMem);
        int newSize = BSPatch.parseNewSize(p);
        newMem = new byte[newSize];
        ByteBuffer new0 = ByteBuffer.wrap(newMem);
        BSPatch.patch(old, new0, p);
    }
}
