import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.*;
import java.util.*;

import static java.lang.Thread.sleep;

public class Sender implements Runnable {

    private String senderIp="127.0.0.1" ;
    private int senderPort = 9876;
    private String receiverIp;
    private int receiverPort;
    private String filename;
    private int MSS;
    private int MWS;
    //用于接收从待发送文件读取的数据
    private volatile byte[]  outBuffer;
    //用于接收收到的UDP数据包
    private volatile byte[] inBuffer;
    //等待多长时间后重发
    private final long resendDelay = 1000;
    //重发次数上限
    private final int maxResendTimes = 3;
    //已发送但未收到确认的数据包缓存 key为数据包，value为发送次数
    private volatile Map<StpPacket, Integer> packetCache;
    //记录下次发送新报文的seq（每次发送新报文时应更新此变量）
    //todo:关于seq溢出后归零，待完善
    private int seq = 1;
    private volatile Timer timer;

    //丢包率
    private double PDrop=0.9;

    /**
     * 记录状态
     * 0： closed
     * 1：syn-sent
     * 2：established
     * 3：fin-wait
     * 4: hasFin-closed
     */
    private volatile int state = 0;
    private final int closed = 0;
    private final int syn_sent = 1;
    private final int established = 2;
    private final int fin_wait = 3;
    private final int hasFin_closed = 4;
    private volatile DatagramSocket socket;

    public Sender(String receiverIp, int receiverPort, String filename, int MSS,double PDrop) throws SocketException, UnknownHostException {
        this.filename = "./resource/" + filename;
        this.MSS = MSS;
        this.MWS = 50 * MSS;
        this.receiverPort = receiverPort;
        this.PDrop=PDrop;
        this.receiverIp = receiverIp;
        this.senderIp=getLocalIpv4Address();
        System.out.println(senderIp);
        this.socket = new DatagramSocket(this.senderPort, InetAddress.getByName(senderIp));
        this.packetCache = new HashMap<>();
        timer = new Timer(true);
        inBuffer = new byte[9 + MSS];
        this.outBuffer = new byte[MSS];
    }

    /**
     * @args[0] receiver IP Host
     * @args[1] receiver port
     * @args[2] file to send
     * @args[3] MSS
     * @args[4] PDrop
     */
    public static void main(String args[]) {
        //
        Sender sender = null;
        try {
            sender = new Sender(args[0], Integer.parseInt(args[1]), args[2], Integer.parseInt(args[3]),Double.parseDouble(args[4]));
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
            this.state = hasFin_closed;
            return;
        }
        System.out.println("建立连接成功，开始传输数据");
        if (!transport()) {
            System.out.println("传输数据失败，程序结束");
            this.state = hasFin_closed;
            return;
        }
        System.out.println("传输数据成功，开始释放连接");
        if (!killconnection()) {
            System.out.println("释放连接失败，程序结束");
            this.state = hasFin_closed;
            return;
        }
        try {
            sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("释放连接成功，程序结束");
    }

    /**
     * 接收报文(线程)
     */
    @Override
    public void run() {
        while (state != hasFin_closed) {
            DatagramPacket datagramPacket = new DatagramPacket(inBuffer, inBuffer.length);
            try {
                socket.receive(datagramPacket);
            } catch (IOException e) {
                System.out.println("接收错误");
            }
            StpPacket stpPacket = new StpPacket(inBuffer);
            handleReceivePacket(stpPacket);
        }
        try {
            sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
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
            case syn_sent:

                if (stpPacket.isSYN()) {
                    StpPacket packetInCache = findPacketFromCacheByAck(stpPacket.getAck());
                    if (packetInCache != null && packetInCache.isSYN()) {
                        System.out.println("收到握手响应");
                        this.state = established;
                        this.packetCache.remove(packetInCache);
                    }
                }
                break;
            case established:
                if ((!stpPacket.isSYN()) && (!stpPacket.isFIN())) {
                    StpPacket packetInCache = findPacketFromCacheByAck(stpPacket.getAck());
                    if (packetInCache != null) {
                        this.packetCache.remove(packetInCache);
                    }
                }
                break;
            case fin_wait:
                if (stpPacket.isFIN()) {
                    StpPacket packetInCache = findPacketFromCacheByAck(stpPacket.getAck());
                    if (packetInCache != null && packetInCache.isFIN()) {
                        this.state = hasFin_closed;
                        this.packetCache.remove(packetInCache);
                    }
                }
                break;
        }
    }

    /**
     * 通过报文对应的确认报文的ack在Cache中寻找原报文
     *
     * @param ack
     * @return
     */
    private StpPacket findPacketFromCacheByAck(int ack) {
        for (StpPacket stpPacket : packetCache.keySet()) {
            int seqInNeed;
            if (stpPacket.getData() == null || stpPacket.getData().length == 0) {
                seqInNeed = ack - 1;
            } else {
                seqInNeed = ack - stpPacket.getData().length;
            }
            if (stpPacket.getSeq() == seqInNeed) {
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
            this.state = isSYN ? syn_sent : fin_wait;
        } catch (IOException e) {
            System.out.println("发送握手报文失败");
            return false;
        }

        long startTime =  System.currentTimeMillis();
        long endTime =  startTime+ resendDelay * maxResendTimes;
        while (state != (isSYN ? established : hasFin_closed)) {
            //一直等待到握手成功

            if (System.currentTimeMillis()>endTime) {
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

        long latestTime = System.currentTimeMillis()+ resendDelay * maxResendTimes * MWS / MSS;

        while (!(isFileFinish && packetCache.size() == 0)) {
            if(state!=2){
                return false;
            }
            if (packetCache.size() != 0 && System.currentTimeMillis()>latestTime) {
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
        }
        latestTime = System.currentTimeMillis() + resendDelay * maxResendTimes * MWS / MSS;

        try {
            fileInputStream.close();
        } catch (IOException e) {
            System.out.println("文件读入流关闭失败");
        }

        return true;
    }

    /**
     * 报文发送封装（发送未发送过的新数据报）
     */
    private synchronized void send(boolean isSYN, boolean isFIN, int seq, int ack, byte[] data) throws IOException {
        StpPacket stpPacket = new StpPacket(isSYN, isFIN, seq, ack, data);

        double random = Math.random();
//            System.out.println("random为："+random);
        if (random > PDrop||data==null) {
            System.out.println("成功发送序号："+stpPacket.getSeq());
            socket.send(new DatagramPacket(stpPacket.toByteArray(), stpPacket.toByteArray().length, InetAddress.getByName(receiverIp), receiverPort));
        }
        else {
            System.out.println("丢包序号："+stpPacket.getSeq());
        }

        if (data == null || data.length == 0) this.seq++;
        else this.seq += data.length;
        packetCache.put(stpPacket, 1);
        timerResend(stpPacket);
    }

    /**
     * 报文发送封装（重发已发送过的数据报）
     */
    private synchronized void send(StpPacket stpPacket) throws IOException {
        double random = Math.random();
        //     System.out.println("重传random为："+random);
        if (random > PDrop||stpPacket.getData()==(null)) {
            System.out.println("成功重传序号："+stpPacket.getSeq());

            socket.send(new DatagramPacket(stpPacket.toByteArray(), stpPacket.toByteArray().length, InetAddress.getByName(receiverIp), receiverPort));
        }
        else {
            System.out.println("重传丢包序号："+stpPacket.getSeq());
        }
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
                if (packetCache.containsKey(stpPacket)&&packetCache.get(stpPacket)!=maxResendTimes&& state != fin_wait) {
                    try {

                        send(stpPacket);

                    } catch (IOException e) {

                    }
                }else if(packetCache.containsKey(stpPacket)&&packetCache.get(stpPacket)==maxResendTimes&&state != fin_wait){

                    packetCache.remove(stpPacket);
                    try {
                        System.out.println("重发失败，停止传输"+stpPacket.getSeq());
                        send(false, true, stpPacket.getSeq(), 0, null);
                        state = fin_wait;
                        timer.cancel();
                    }catch (IOException e){
                        System.out.println("发送握手报文失败");

                    }

                }
            }
        }, resendDelay);
    }

    public static String getLocalIpv4Address() throws SocketException {
        String localip = null;// 本地IP，如果没有配置外网IP则返回它
        String netip = null;// 外网IP
        Enumeration<NetworkInterface> netInterfaces = null;
        try {
            netInterfaces = NetworkInterface.getNetworkInterfaces();
        } catch (SocketException e) {
            e.printStackTrace();
        }
        InetAddress ip = null;
        boolean finded = false;// 是否找到外网IP
        while (netInterfaces.hasMoreElements() && !finded) {
            NetworkInterface ni = netInterfaces.nextElement();
            Enumeration<InetAddress> address = ni.getInetAddresses();
            while (address.hasMoreElements()) {
                ip = address.nextElement();
                if (!ip.isSiteLocalAddress()
                        && !ip.isLoopbackAddress()
                        && ip.getHostAddress().indexOf(":") == -1) {// 外网IP
                    netip = ip.getHostAddress();
                    finded = true;
                    break;
                } else if (ip.isSiteLocalAddress()
                        && !ip.isLoopbackAddress()
                        && ip.getHostAddress().indexOf(":") == -1) {// 内网IP
                    localip = ip.getHostAddress();
                }
            }
        }
        if (netip != null && !"".equals(netip)) {
            return netip;
        } else {
            return localip;
        }
    }
}