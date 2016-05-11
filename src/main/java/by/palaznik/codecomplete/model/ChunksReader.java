package by.palaznik.codecomplete.model;

import org.apache.commons.io.FileUtils;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.LinkedList;
import java.util.Queue;

public class ChunksReader {
    private final int MAX_BUFFERED_HEADERS = 100_000;
    private static final int MAX_SIZE = 1_048_576 * 64;

    private int chunkIndex;
    private int fileIndex;
    private int size;
    private int generation;
//    private int dataBufferPosition;

    private String headersFileName;
    private String dataFileName;

    private FileChannel headersChannel;
    private FileChannel dataChannel;
    private ByteBuffer dataBuffer;
//    private BufferedInputStream dataStream;
    private Queue<ChunkHeader> headersBuffer;

    public ChunksReader(String dataFileName, int size, int generation) {
        this.size = size;
        this.fileIndex = 0;
        this.chunkIndex = 0;
//        this.dataBufferPosition = 0;
        this.dataFileName = dataFileName;
        this.headersFileName = "headers_" + dataFileName;
        this.generation = generation;
        this.headersBuffer = new LinkedList<>();
        this.dataBuffer = ByteBuffer.allocate(MAX_SIZE);
    }

    public ChunksReader(String dataFileName, int size) {
        this(dataFileName, size, 0);
    }

    public int getGeneration() {
        return generation;
    }

    public boolean equalGenerationWith(ChunksReader reader) {
        return this.generation == reader.getGeneration();
    }

    public void openFiles() {
        headersChannel = openChannel(headersFileName);
        dataChannel = openChannel(dataFileName);
        readDataToBuffer();
//        dataStream = openStream(dataFileName);
    }

    private BufferedInputStream openStream(String fileName) {
        BufferedInputStream stream = null;
        try {
            stream = new BufferedInputStream(new FileInputStream(fileName));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return stream;
    }

    private FileChannel openChannel(String fileName) {
        FileChannel channel = null;
        try {
            channel = new RandomAccessFile(fileName, "rw").getChannel();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return channel;
    }

    public int getCurrentNumber() {
        ChunkHeader header = getCurrentHeader();
        if (header != null) {
            return header.getBeginNumber();
        }
        return -1;
    }

    private ChunkHeader getCurrentHeader() {
        if (headersBuffer.size() == 0) {
            readNextHeaders();
        }
        return headersBuffer.peek();
    }

    private void readNextHeaders() {
        int restSize = Math.min(MAX_BUFFERED_HEADERS, restChunksInFile());
        ByteBuffer bytes = ByteBuffer.allocate(12 * restSize);
        try {
            headersChannel.read(bytes);
        } catch (IOException e) {
            e.printStackTrace();
        }
        bytes.flip();
        for (int i = 0; i < restSize; i++, fileIndex++) {
            headersBuffer.add(new ChunkHeader(bytes.getInt(), bytes.getInt(), bytes.getInt()));
        }
    }

    private int restChunksInFile() {
        return size - fileIndex;
    }

    public void copyChunks(ChunksWriter merged, int upperBound) throws IOException {
        int bytesAmount = 0;
        do {
            ChunkHeader currentHeader = getCurrentHeader();
            bytesAmount += currentHeader.getBytesAmount();
            merged.addHeader(currentHeader);
            chunkIndex++;
            removeHeader();
        } while (hasMoreSequenceChunks(upperBound));
        if (dataBuffer.position() + bytesAmount > MAX_SIZE) {
            removeBytesFromStart();
            readDataToBuffer();
//            dataBufferPosition = 0;
        }
        byte[] bytes = new byte[bytesAmount];
//        dataBufferPosition += bytesAmount;
        dataBuffer.get(bytes);
        merged.transferBytesFrom(bytes);
    }

    private void readDataToBuffer() {
        try {
            dataChannel.read(dataBuffer);
        } catch (IOException e) {
            e.printStackTrace();
        }
        dataBuffer.clear();
    }

    private void removeBytesFromStart() {
        for(int i = dataBuffer.position(), index = 0; i < dataBuffer.limit(); i++, index++) {
            dataBuffer.put(index, dataBuffer.get(i));
            dataBuffer.put(i, (byte)0);
        }
        dataBuffer.position(dataBuffer.limit() - dataBuffer.position());
    }

    private void removeHeader() {
        headersBuffer.poll();
    }

    private boolean hasMoreSequenceChunks(int upperBound) {
        return hasMoreChunks() && ((getCurrentNumber() < upperBound) || upperBound == -1);
    }

    public boolean hasMoreChunks() {
        return chunkIndex < size;
    }

    public void renameFileToMerged() {
        closeFiles();
        Path from = Paths.get(dataFileName);
        Path to = Paths.get("merged.txt");
        try {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            e.printStackTrace();
        }
        FileUtils.deleteQuietly(new File(headersFileName));
    }

    private void closeFiles() {
//        closeStream(dataStream);
        closeChannel(dataChannel);
        closeChannel(headersChannel);
    }

    private static void closeStream(BufferedInputStream stream) {
        try {
            if (stream != null) {
                stream.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void closeChannel(FileChannel channel) {
        try {
            if (channel != null) {
                channel.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void deleteStreams() {
        closeFiles();
        FileUtils.deleteQuietly(new File(dataFileName));
        FileUtils.deleteQuietly(new File(headersFileName));
    }
}
