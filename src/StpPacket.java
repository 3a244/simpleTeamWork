public class StpPacket {
    /**
     * header格式说明
     * [0]:包含标志位 第一位为SYN 第二位是FIN 剩余六位暂时闲置
     * [1]~[4] : 32位的seq
     * [5]~[8]: 32位的ack
     * 综上，header固定长度为9字节，即此数组长度为9
     */
    private byte[] header;

    /**
     * data部分长度不一
     */
    private byte[] data;

    /**
     * 发送报文时 报文的构造函数
     *
     * @param isSYN
     * @param isFIN
     * @param seq
     * @param ack
     * @param data
     */
    public StpPacket(boolean isSYN, boolean isFIN, int seq, int ack, byte[] data) {
        /**
         * 10000000  -128
         * 11000000
         * 01000000  64
         * 00000000
         */
        header = new byte[9];
        byte sign = 0;
        if (isSYN) sign -= 128;
        if (isFIN) sign += 64;
        header[0] = sign;
        byte[] seqBytes = intToByte4(seq);
        byte[] ackBytes = intToByte4(ack);
        for (int i = 0; i < 4; i++) {
            header[i + 1] = seqBytes[i];
            header[i + 5] = ackBytes[i];
        }
        if (data != null) {
            this.data = data.clone();
        }
    }

    /**
     * 接收报文时，根据字节数组生成报文的构造函数
     *
     * @param buffer
     */
    public StpPacket(byte[] buffer) {
        header = new byte[9];
        for (int i = 0; i < 9; i++) {
            header[i] = buffer[i];
        }
        data = null;
        if (buffer.length > 9) {
            data = new byte[buffer.length - 9];
            for (int i = 0; i < data.length; i++) {
                data[i] = buffer[i + 9];
            }
        }
    }

    /**
     * 序列化为字节数组 以发送
     *
     * @return
     */
    public byte[] toByteArray() {
        byte[] res = null;
        int len = 0;
        if (data != null) {
            len = header.length + data.length;
        } else {
            len = header.length;
        }
        res = new byte[len];
        for (int i = 0; i < 9; i++) {
            res[i] = header[i];
        }
        for (int i = 9; i < len; i++) {
            res[i] = data[i - 9];
        }
        return res;
    }

    public byte[] getData() {
        return data;
    }

    public boolean isSYN() {
        return (header[0] & 0b10000000) == 0b10000000;
    }

    public boolean isFIN() {
        return (header[0] & 0b01000000) == 0b01000000;
    }

    public int getSeq() {
        String str = "";
        for (int i = 1; i <= 4; i++) {
            str += byteToString(header[i]);
        }
        return Integer.parseInt(str, 2);
    }

    public int getAck() {
        String str = "";
        for (int i = 5; i <= 8; i++) {
            str += byteToString(header[i]);
        }
        return Integer.parseInt(str, 2);
    }

    private byte[] intToByte4(int i) {
        byte[] targets = new byte[4];
        targets[3] = (byte) (i & 0xFF);
        targets[2] = (byte) (i >> 8 & 0xFF);
        targets[1] = (byte) (i >> 16 & 0xFF);
        targets[0] = (byte) (i >> 24 & 0xFF);
        return targets;
    }

    private String byteToString(byte n) {
        return Integer.toBinaryString((n & 0xFF) + 0x100).substring(1);
    }
}
