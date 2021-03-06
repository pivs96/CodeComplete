package by.palaznik.codecomplete.service;

import by.palaznik.codecomplete.model.Chunk;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.*;
import java.util.Base64;

import static by.palaznik.codecomplete.controller.ChunksServletTest.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class ChunksServiceTest {

    private MemoryCheck memory;
    private Thread thread;

    @Before
    public void setUp() throws Exception {
        memory = new MemoryCheck();
        thread = new Thread(memory);
        thread.start();
    }

    @Test
    public void sendChunks() throws Exception {
        testChunks(1, true);
    }

    private void testChunks(int amount, boolean shuffle) {
        sendChunks(amount, shuffle);
        endMemoryCheck();
        testValues(amount);
    }

    private void sendChunks(int amount, boolean shuffle) {
        int[] numbers = getNumbers(amount, shuffle);
        String line = getAlphabetLine().toString();
        for (int number : numbers) {
            String data = number + line;
            String dataBase64 = new String(Base64.getEncoder().encode(data.getBytes()));
            boolean isLast = false;
            if (number == amount - 1) {
                isLast = true;
            }
            Chunk chunk = new Chunk(number, dataBase64, isLast);
            ChunksService.setEndIfLast(chunk);
            ChunksService.addToBuffer(chunk);
        }
    }

    private void endMemoryCheck() {
        memory.setRunning(false);
        try {
            thread.join();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void testValues(int amount) {
        try(BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream("merged.txt")))) {
            String line;
            int i = 0;
            while ((line = reader.readLine()) != null ) {
                int spaceIndex = line.indexOf(" ");
                assertNotEquals(-1, spaceIndex);
                int number = Integer.valueOf(line.substring(0, spaceIndex));
                assertEquals(i, number);
                i++;
            }
            assertEquals(i, amount);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}