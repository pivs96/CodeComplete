package by.palaznik.codecomplete.service;

import by.palaznik.codecomplete.model.*;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.*;

public class FileService {
    private static final int MAX_SIZE = 1_048_576 * 16;
    private static Comparator<Chunk> chunkComparator = (Chunk first, Chunk second) -> first.getNumber() - second.getNumber();

    private static List<Chunk> bufferedChunks = new ArrayList<>();
    private static Deque<ChunksReader> chunksReaders = new LinkedList<>();

    private static int count = 0;
    private static int bytesSize = 0;
    private static int end = -1;
    private static int fileNumber = 0;

    public static boolean checkHash(Chunk chunk, String hash) {
        String dataHash = DigestUtils.md5Hex(chunk.getData());
        return hash.equals(dataHash);
    }

    public static void setEndIfLast(Chunk chunk) {
        if (chunk.isLast()) {
            end = chunk.getNumber();
        }
    }

    public static void addToBuffer(Chunk chunk) {
        bufferedChunks.add(chunk);
        count++;
        bytesSize += chunk.getData().length + 12;
        boolean isEndOfChunks = (count - 1 == end);
        if (isFullBuffer() || isEndOfChunks) {
            writeBuffer();
            if (isEndOfChunks) {
                mergeFilesForFinal();
            } else {
                mergeFilesWithSameGenerations();
            }
        }
    }

    private static boolean isFullBuffer() {
        return bytesSize >= MAX_SIZE;
    }

    private static void writeBuffer() {
        try {
            writeToFile();
            bytesSize = 0;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void writeToFile() throws IOException {
//        String fileName = fileNumber++ + ".txt";
        ByteBuffer dataBuffer = ByteBuffer.allocate(bytesSize + bufferedChunks.size() * 12);
        writeChunksToBuffer(dataBuffer);
        chunksReaders.addLast(new ChunksBufferReader(bufferedChunks, dataBuffer));
        bufferedChunks = new ArrayList<>();
    }

    private static void writeChunksToBuffer(ByteBuffer dataBuffer) {
        sortChunks();
        for (Chunk chunk : bufferedChunks) {
            dataBuffer.put(chunk.getData());
        }
        dataBuffer.flip();
    }

    private static void sortChunks() {
        Collections.sort(bufferedChunks, chunkComparator);
    }

    private static void mergeFilesForFinal() {
        count = 0;
        boolean isFinal = false;
        while (chunksReaders.size() > 1) {
            if (chunksReaders.size() == 2) {
                isFinal = true;
            }
            mergeFiles(isFinal);
        }
    }

    private static void mergeFilesWithSameGenerations() {
        while (hasSameGenerations()) {
            mergeFiles(false);
        }
    }

    private static void mergeFiles(boolean isFinal) {
        ChunksReader first = getChunksReader();
        ChunksReader second = getChunksReader();
        merge(first, second, isFinal);
        first.deleteResources();
        second.deleteResources();
    }

    private static ChunksReader getChunksReader() {
        ChunksReader reader = chunksReaders.pollLast();
        reader.openResources();
        return reader;
    }

    private static boolean hasSameGenerations() {
        if (chunksReaders.size() < 2) {
            return false;
        }
        ChunksReader last = chunksReaders.pollLast();
        boolean sameGenerations = false;
        if (last.equalGenerationWith(chunksReaders.peekLast())) {
            sameGenerations = true;
        }
        chunksReaders.addLast(last);
        return sameGenerations;
    }

    private static void merge(ChunksReader first, ChunksReader second, boolean isFinal) {
        String fileName = fileNumber++ + ".txt";
        try {
            if (isFinal) {
                mergeToFile(first, second, new ChunksWriterFinal(fileName));
            } else {
                int mergedSize = mergeToFile(first, second, new ChunksWriter(fileName));
                chunksReaders.add(new ChunksFileReader(fileName, mergedSize, first.getGeneration() + 1));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static int mergeToFile(ChunksReader first, ChunksReader second, ChunksWriter merged) throws IOException {
        int firstNumber = first.getCurrentNumber();
        int secondNumber = second.getCurrentNumber();
        boolean isMainSequence = firstNumber < secondNumber;
        merged.openFile();
        while (first.hasMoreChunks() || second.hasMoreChunks()) {
            if (isMainSequence) {
                firstNumber = copyChunks(merged, first, secondNumber);
            } else {
                secondNumber = copyChunks(merged, second, firstNumber);
            }
            isMainSequence = !isMainSequence;
        }
        merged.writeBufferedChunks();
        merged.closeFile();
        return merged.getHeadersAmount();
    }

    private static int copyChunks(ChunksWriter merged, ChunksReader current, int upperBound) throws IOException {
        current.copyChunks(merged, upperBound);
        return current.getCurrentNumber();
    }
}
