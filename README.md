# bsdiff-java

[bsdiff v4.3](https://github.com/MarsHou/bsdiff-4.3) Java implementation ( the core diff and patch code is hard-code translated )

### Hightlights

1. support low memory useage (NIO file mapping)
2. support memory diff & path and write to memory

# Disclaimer

Although I did some tests like `diff` in Java and then patch using `bsdpatch` command on the terminal or vice versa, but this does not guarantee that the code is fully compatible with the C version.

The code is not guaranteed to be usable in all situations and has not been extensively tested.

# Examples

```java
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

public void memoryDiff() throws IOException {
    byte[] oldMem = null;
    byte[] newMem = null;

    ByteBuffer old = ByteBuffer.wrap(oldMem);
    ByteBuffer new0 = ByteBuffer.wrap(newMem);

    SeekableByteArrayOutputStream sbos = new SeekableByteArrayOutputStream();
    BSDiff.diff(old, new0, sbos, ByteBuffer::allocate);
}

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
```
