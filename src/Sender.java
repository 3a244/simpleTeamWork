import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.*;
import java.util.*;

public class Sender implements Runnable {

    private String senderIp = "127.0.0.1";
    private int senderPort = 9876;
    private String receiverIp;
    private int receiverPort;
    private String filename;
    private int MSS;
    private int MWS;
    //用于接收从待发送文件读取的数据
    private byte[] outBuffer;
    //用于接收收到的UDP数据包
    private byte[] inBuffer;
    //等待多长时间后重发
    private final long resendDelay = 1000;
    //重发次数上限
    private final int maxResendTimes = 3;
    //已发送但未收到确认的数据包缓存 key为数据包，value为发送次数
    private Map<StpPacket, Integer> packetCache;
    //记录下次发送新报文的seq（每次发送新报文时应更新此变量）
    private int seq = 1;
    private Timer timer;

    /**
     * 记录状态
     * 0： closed
     * 1：syn-sent
     * 2：established
     * 3：fin-wait
     * 4: hasFin-closed
     */
    private int state = 0;

    private DatagramSocket socket;

    public Sender(String receiverIp, int receiverPort, String filename, int MSS) throws SocketException, UnknownHostException {
        this.filename = "./resource/" + filename;
        this.MSS = MSS;
        this.MWS = 5 * MSS;
        this.receiverPort = receiverPort;
        this.receiverIp = receiverIp;
        this.socket = new DatagramSocket(this.senderPort, InetAddress.getByName(senderIp));
        this.packetCache = new HashMap<>();
        timer = new Timer();
        inBuffer = new byte[9 + MSS];
        this.outBuffer = new byte[MSS];
    }

    /**
     * @args[0] receiver IP Host
     * @args[1] receiver port
     * @args[2] file to send
     * @args[3] MSS
     */
    public static void main(String args[]) {
        //
        Sender sender = null;
        try {
            sender = new Sender(args[0], Integer.parseInt(args[1]), args[2], Integer.parseInt(args[3]));

        } catch (SocketException e) {
            System.out.println("socket建立失败");
            return;
        } catch (UnknownHostException e) {
            System.out.println("无效的IP地址");
            return;
        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.println("参数数量不符合要求");
            return;
        } catch (NumberFormatException e) {
            System.out.println("参数格式不符合要求");
            return;
        }
        System.out.println("socket初始化成功");
        sender.start();

    }

    public void start() {
        Thread t = new Thread(this);
        t.start();
        if (!establishConnection()) {
            System.out.println("建立连接失败，程序结束");
            this.state = 4;
            return;
        }
        System.out.println("建立连接成功，开始传输数据");
        if (!transport()) {
            System.out.println("传输数据失败，程序结束");
            this.state = 4;
            return;
        }
        System.out.println("传输数据成功，开始释放连接");
        if (!killconnection()){
            System.out.println("释放连接失败，程序结束");
            this.state = 4;
            return;
        }
        System.out.println("释放连接成功，程序结束");
    }

    /**
     * 接收报文(线程)
     */
    @Override
    public void run() {
        while (state != 4) {
            DatagramPacket datagramPacket = new DatagramPacket(inBuffer, inBuffer.length);
            try {
                socket.receive(datagramPacket);
            } catch (IOException e) {
                System.out.println("接收错误");
            }
            StpPacket stpPacket = new StpPacket(inBuffer);
            handleReceivePacket(stpPacket);
        }
        System.out.println("结束接收监听");
    }

    private synchronized void handleReceivePacket(StpPacket stpPacket) {
        /**
         * 记录状态
         * 0： closed
         * 1：syn-sent
         * 2：established
         * 3：fin-wait
         * 4: hasFin-closed
         */
        switch (state) {
            case 1:
                if (stpPacket.isSYN()) {
                    StpPacket packetInCache = findPacketFromCacheBySeq(stpPacket.getAck() - 1);
                    if (packetInCache != null && packetInCache.isSYN()) {
                        this.state = 2;
                        this.packetCache.remove(packetInCache);
                    }
                }
                break;
            case 2:
                if ((!stpPacket.isSYN()) && (!stpPacket.isFIN())) {
                    StpPacket packetInCache = findPacketFromCacheBySeq(stpPacket.getAck() - 1);
                    if (packetInCache != null) {
                        this.packetCache.remove(packetInCache);
                    }
                }
                break;
            case 3:
                if (stpPacket.isFIN()) {
                    StpPacket packetInCache = findPacketFromCacheBySeq(stpPacket.getAck() - 1);
                    if (packetInCache != null && packetInCache.isFIN()) {
                        this.state = 4;
                        this.packetCache.remove(packetInCache);
                    }
                }
                break;
        }
    }

    private StpPacket findPacketFromCacheBySeq(int seq) {
        for (StpPacket stpPacket : packetCache.keySet()) {
            if (stpPacket.getSeq() == seq) {
                return stpPacket;
            }
        }
        return null;
    }

    private boolean establishConnection() {
        return handShake(true);
    }
    private boolean killconnection() {
        return handShake(false);
    }

    /**
     * 对握手的封装
     * 参数为true时，为建立连接的握手。否则为释放连接的握手
     *
     * @param isSYN
     * @return
     */
    private boolean handShake(boolean isSYN) {
        try {
            send(isSYN, !isSYN, seq, 0, null);
            this.state = isSYN ? 1 : 3;
        } catch (IOException e) {
            System.out.println("发送握手报文失败");
            return false;
        }
        Date date = new Date();
        date.setTime(date.getTime() + resendDelay * maxResendTimes);

        while (state != (isSYN ? 2 : 4)) {
            //一直等待到握手成功
            if (new Date().after(date)) {
                System.out.println("握手超时");
                return false;
            }
        }
        return true;
    }

    private boolean transport() {
        FileInputStream fileInputStream = null;
        try {
            fileInputStream = new FileInputStream(filename);
        } catch (FileNotFoundException e) {
            System.out.println("发送文件名找不到");
            return false;
        }
        boolean isFileFinish = false;
        //若超过这个时间，窗口内报文仍未全部收到确认，则退出
        Date latestTime = null;
        while (!(isFileFinish && packetCache.size() == 0)) {
            if (packetCache.size() != 0 && new Date().after(latestTime)) {
                System.out.println("等待确认报文超时");
                return false;
            }
            if (packetCache.size() != 0) {
                //如果缓存不为空，即窗口内仍未均收到
                continue;
            }
            for (int i = 0; i < MWS / MSS; i++) {
                int bufferLen = -1;
                try {
                    bufferLen = fileInputStream.read(outBuffer);
                } catch (IOException e) {
                    System.out.println("读取待发送文件失败");
                    return false;
                }

                if (bufferLen == -1) {
                    isFileFinish = true;
                    break;
                }
                //outBuffer不一定填满，因此作处理，干掉多余部分
                byte[] data = null;
                if (bufferLen == outBuffer.length) {
                    data = outBuffer;
                } else {
                    data = new byte[bufferLen];
                    for (int j = 0; j < bufferLen; j++) {
                        data[j] = outBuffer[j];
                    }
                }
                try {
                    this.send(false, false, seq, 0, data);
                } catch (IOException e) {
                    System.out.println("发送数据失败");
                    return false;
                }

            }
            latestTime = new Date();
            latestTime.setTime(latestTime.getTime() + resendDelay * maxResendTimes * MWS / MSS);
        }
        return true;
    }

    /**
     * 报文发送封装（发送未发送过的新数据报）
     */
    private synchronized void send(boolean isSYN, boolean isFIN, int seq, int ack, byte[] data) throws IOException {
        StpPacket stpPacket = new StpPacket(isSYN, isFIN, seq, ack, data);
        socket.send(new DatagramPacket(stpPacket.toByteArray(), stpPacket.toByteArray().length, InetAddress.getByName(receiverIp), receiverPort));
        if (data == null || data.length == 0) this.seq++;
        else this.seq += data.length;
        packetCache.put(stpPacket, 1);
        timerResend(stpPacket);
    }

    /**
     * 报文发送封装（重发已发送过的数据报）
     */
    private synchronized void send(StpPacket stpPacket) throws IOException {
        if (packetCache.get(stpPacket) == maxResendTimes) {
            //不知道怎样结束程序……被多线程搞晕了
            throw new IOException();
        }
        socket.send(new DatagramPacket(stpPacket.toByteArray(), stpPacket.toByteArray().length, InetAddress.getByName(receiverIp), receiverPort));
        packetCache.put(stpPacket, packetCache.get(stpPacket) + 1);
        timerResend(stpPacket);

    }

    /**
     * 设置定时重传
     *
     * @param stpPacket
     */
    private void timerResend(StpPacket stpPacket) {
        timer.schedule(new TimerTask() {
            @Override
            public synchronized void run() {
                //如果缓存区还有此数据包，即还未收到确认，才会重发
                if (packetCache.containsKey(stpPacket)) {
                    try {
                        send(stpPacket);
                    } catch (IOException e) {

                    }
                }
            }
        }, resendDelay);
    }

}
