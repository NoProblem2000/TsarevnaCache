package io.github.noproblem2000.tsarevnacache.logs;

import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

/**
 * Reads a file line by line from its end towards its start with a {@link RandomAccessFile}, so the
 * tail of a file of any size costs only the bytes actually read. Lines are split at {@code '\n'}
 * on the byte level (safe for UTF-8) and decoded afterwards; a trailing {@code '\r'} is dropped.
 *
 * <p>The reader works on the first {@code length} bytes only, so a file that keeps growing while
 * it is read is seen consistently. Lines longer than {@code maxLineBytes} keep their first bytes.
 */
final class ReverseLineReader implements Closeable {

    private static final int CHUNK_SIZE = 64 * 1024;
    private static final byte[] EMPTY = new byte[0];

    private final RandomAccessFile file;
    private final int maxLineBytes;
    /** Complete lines of the last chunk, the latest first. */
    private final Deque<byte[]> ready = new ArrayDeque<>();
    /** Bytes before this position have not been read yet. */
    private long position;
    /** The start of the line that continues in the part already read; {@code null} once returned. */
    private byte[] carry;
    private boolean firstChunk = true;
    private long scannedBytes;

    ReverseLineReader(Path path, long length, int maxLineBytes) throws IOException {
        this.file = new RandomAccessFile(path.toFile(), "r");
        this.maxLineBytes = maxLineBytes;
        this.position = length;
        this.carry = length == 0 ? null : EMPTY;
    }

    /** The previous line, {@code null} when the start of the file has been passed. */
    String previous() throws IOException {
        while (ready.isEmpty()) {
            if (position == 0) {
                if (carry == null) {
                    return null;
                }
                byte[] first = carry;
                carry = null;
                return decode(first);
            }
            readChunk();
        }
        return decode(ready.pollFirst());
    }

    /** Bytes read from the file so far. */
    long scannedBytes() {
        return scannedBytes;
    }

    /** Whether lines of the chunks already read are still to be returned. */
    boolean hasBufferedLines() {
        return !ready.isEmpty();
    }

    /** Whether every line of the file has been returned. */
    boolean reachedStart() {
        return position == 0 && carry == null && ready.isEmpty();
    }

    private void readChunk() throws IOException {
        int size = (int) Math.min(CHUNK_SIZE, position);
        position -= size;
        byte[] buffer = new byte[size];
        file.seek(position);
        file.readFully(buffer);
        scannedBytes += size;

        int end = size;
        if (firstChunk) {
            firstChunk = false;
            // the newline that terminates the last line does not start another (empty) line
            if (end > 0 && buffer[end - 1] == '\n') {
                end--;
            }
        }
        for (int i = end - 1; i >= 0; i--) {
            if (buffer[i] == '\n') {
                ready.addLast(join(buffer, i + 1, end, carry));
                carry = EMPTY;
                end = i;
            }
        }
        carry = join(buffer, 0, end, carry);
    }

    /** {@code buffer[from, to)} followed by {@code tail}, cut to {@link #maxLineBytes}. */
    private byte[] join(byte[] buffer, int from, int to, byte[] tail) {
        int head = to - from;
        int length = Math.min(maxLineBytes, head + tail.length);
        byte[] line = Arrays.copyOfRange(buffer, from, from + Math.min(head, length));
        if (line.length < length) {
            line = Arrays.copyOf(line, length);
            System.arraycopy(tail, 0, line, head, length - head);
        }
        return line;
    }

    private static String decode(byte[] line) {
        int length = line.length;
        if (length > 0 && line[length - 1] == '\r') {
            length--;
        }
        return new String(line, 0, length, StandardCharsets.UTF_8);
    }

    @Override
    public void close() throws IOException {
        file.close();
    }
}
