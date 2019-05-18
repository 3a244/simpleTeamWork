import java.io.IOException;
import java.net.*;
import java.util.*;

public class Sender implements Runnable {

    private String senderIp="127.0.0.1";
    private int senderPort=9876;
    private String reciverIp;
    private int reciverPort;
    private String filename;
    private int MSS;
    private int MWS;
    //用于接收收到的UDP数据包
    private byte[] inBuffer;
    //等待多长时间后重发
    private final long resendDelay=1000;
    //重发次数上限
    private final int maxResendTimes=5;
    //已发送但未收到确认的数据包缓存 key为数据包，value为发送次数
    private Map<StpPacket,Integer> packetCache;
    //记录下次发送新报文的seq（每次发送新报文时应更新此变量）
    private int seq=1;
    private Timer timer;

    /**
     * 记录状态
     * 0： closed
     * 1：syn-sent
     * 2：established
     * 3：fin-wait
     * 4: hasFin-closed
     */
    private int state=0;

    private DatagramSocket socket;

    public Sender(String reciverIp, int reciverPort, String filename, int MSS) throws SocketException, UnknownHostException {
        this.filename = "./resource/" + filename;
        this.MSS = MSS;
        this.MWS = 5 * MSS;
        this.reciverPort = reciverPort;
        this.reciverIp = reciverIp;
        this.socket = new DatagramSocket(this.senderPort, InetAddress.getByName(senderIp));
        this.packetCache=new HashMap<>();
        timer=new Timer();
        inBuffer=new byte[9+MSS];
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
        Thread s=new Thread(sender);
        s.start();

    }

    public void start() {
        Thread t = new Thread(this);
        t.run();

        handshake();
        transport();
        killconnection();
    }

    /**
     * 接收报文(线程)
     */
    @Override
    public void run() {
        while (state!=4){
            DatagramPacket datagramPacket=new DatagramPacket(inBuffer,inBuffer.length);
            try {
                socket.receive(datagramPacket);
            }catch (IOException e){
                System.out.println("接收错误");
            }
            StpPacket stpPacket=new StpPacket(inBuffer);
            handleReceivePacket(stpPacket);

        }

    }
    private synchronized void handleReceivePacket(StpPacket stpPacket){
        /**
         * 记录状态
         * 0： closed
         * 1：syn-sent
         * 2：established
         * 3：fin-wait
         * 4: hasFin-closed
         */
        switch (state){
            case 1:

                break;
            case 2:
                break;
            case 3:
                break;
        }
    }
    private StpPacket findPacketFromCacheBySeq(int seq){
        for (StpPacket stpPacket:packetCache.keySet()){
            if (stpPacket.getSeq()==seq){
                return stpPacket;
            }
        }
        return null;
    }

    private boolean handshake() {
        try {
            send(true,false,seq,0,null);
            this.state=1;
            return true;
        }catch (IOException e){
            return false;
        }
    }

    private boolean transport() {

        return false;
    }

    /**
     * 报文发送封装（发送未发送过的新数据报）
     */
    private synchronized void send(boolean isSYN,boolean isFIN,int seq,int ack,byte[] data)throws IOException {
        StpPacket stpPacket=new StpPacket(isSYN,isFIN,seq,ack,data);
        socket.send(new DatagramPacket(stpPacket.toByteArray(),stpPacket.toByteArray().length,InetAddress.getByName(reciverIp),reciverPort));
        if (data==null||data.length==0)this.seq++;
        else this.seq+=data.length;
        packetCache.put(stpPacket,1);
        timerResend(stpPacket);
    }
    /**
     * 报文发送封装（重发已发送过的数据报）
     */
    private synchronized void send(StpPacket stpPacket)throws IOException {
        if (packetCache.get(stpPacket)==maxResendTimes){
            //不知道怎样结束程序……被多线程搞晕了
            throw new IOException();
        }
        socket.send(new DatagramPacket(stpPacket.toByteArray(),stpPacket.toByteArray().length,InetAddress.getByName(reciverIp),reciverPort));
        packetCache.put(stpPacket,packetCache.get(stpPacket)+1);
        timerResend(stpPacket);

    }

    /**
     * 设置定时重传
     * @param stpPacket
     */
    private void timerResend(StpPacket stpPacket){
        timer.schedule(new TimerTask() {
            @Override
            public synchronized void run() {
                //如果缓存区还有此数据包，即还未收到确认，才会重发
                if (packetCache.containsKey(stpPacket)){
                    try{
                        send(stpPacket);
                    }catch (IOException e){

                    }
                }
            }
        },resendDelay);
    }

    private boolean killconnection() {
        return false;
    }

}
