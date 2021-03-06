package by.palaznik.codecomplete.model;

public class ChunkHeader {
    private int beginNumber;
    private int endNumber;
    private int bytesAmount;

    public ChunkHeader(int beginNumber, int endNumber, int bytesAmount) {
        this.bytesAmount = bytesAmount;
        this.beginNumber = beginNumber;
        this.endNumber = endNumber;
    }

    public int getBeginNumber() {
        return beginNumber;
    }

    public int getBytesAmount() {
        return bytesAmount;
    }

    public int getEndNumber() {
        return endNumber;
    }

    public boolean isNextTo(ChunkHeader previous) {
        return (previous != null) && this.beginNumber - 1 == previous.getEndNumber();
    }
}
