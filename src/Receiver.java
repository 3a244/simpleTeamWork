import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.*;
import java.util.HashMap;

public class Receiver {
    private String receiverIp = "127.0.0.1";
    private int receiverPort;
    private String senderIp;
    private int senderPort;
    private DatagramSocket socket;
    private byte[] buffer;
    private String filename;
    private int MSS;
    private int ack = 1;
    //缓存未按序到达的数据报 key为seq，便于检索
    private HashMap<Integer, StpPacket> disorderPacketCache;
    /**
     * 记录状态
     * 0： closed
     * 1：established
     * 2: hasFin-closed
     */
    private int state;

    public Receiver(int receiverPort, String filename, int MSS) throws SocketException, UnknownHostException {
        this.filename = "./resource/" + filename;
        this.MSS = MSS;
        this.receiverPort = receiverPort;
        this.socket = new DatagramSocket(this.receiverPort, InetAddress.getByName(this.receiverIp));
        this.buffer = new byte[9 + MSS];
        this.disorderPacketCache = new HashMap<>();
    }

    /**
     * @args[0] receiver IP port
     * @args[1] file to store
     * @args[2] MSS
     */
    public static void main(String args[]) {
        //
        Receiver receiver = null;
        try {
            receiver = new Receiver(Integer.parseInt(args[0]), args[1], Integer.parseInt(args[2]));
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
        receiver.go();
    }

    public void go() {
        System.out.println("开始监听接收报文");
        while (state != 2) {
            receive();
        }
    }

    private boolean receive() {
        DatagramPacket datagramPacket = new DatagramPacket(buffer, buffer.length);
        try {
            socket.receive(datagramPacket);
        } catch (IOException e) {
            System.out.println("接收错误");
        }

        handleReceivePacket(new StpPacket(buffer), datagramPacket.getAddress().getHostName(), datagramPacket.getPort());
        return true;
    }

    private boolean handleReceivePacket(StpPacket stpPacket, String hostName, int port) {
        switch (this.state) {
            case 0:
                //握手，建立同步
                if (stpPacket.isSYN() && (stpPacket.getData() == null || stpPacket.getData().length == 0)) {
                    //根据握手报文确定发送方的地址与端口
                    this.senderIp = hostName;
                    this.senderPort = port;
                    //初始ack应该由sender发送的seq决定
                    this.ack = stpPacket.getSeq() + 1;
                    sendAck(true, false, 0, this.ack);
                    this.state = 1;
                    System.out.println("握手成功，建立连接");
                }
                break;
            case 1:
                //接收数据，发送响应
                if ((!stpPacket.isSYN()) && (!stpPacket.isFIN())) {
                    if (stpPacket.getSeq() == ack) {
                        //如果收到的数据报文是期望顺序中的下一个
                        System.out.println("按顺序收到一个报文");
                        this.ack += stpPacket.getData().length;
                        sendAck(false, false, 0, ack);
                        writeFile(stpPacket.getData());
                        //检查期望的下一个数据报是否已在缓存中，若在则写入文件
                        while (disorderPacketCache.get(ack) != null) {
                            StpPacket packetInCache = disorderPacketCache.get(ack);
                            writeFile(packetInCache.getData());
                            this.ack += packetInCache.getData().length;
                            disorderPacketCache.remove(packetInCache.getSeq());
                        }
                    }else {
                        //如果收到的数据报不是期望顺序的下一个，则缓存,不改变this.ack
                        sendAck(false, false, 0, stpPacket.getSeq()+stpPacket.getData().length);
                        disorderPacketCache.put(stpPacket.getSeq(),stpPacket);
                    }
                } else if (stpPacket.isFIN()&&stpPacket.getSeq()==ack) {
                    this.ack++;
                    //发送结束完成响应
                    sendAck(false, true, 0, ack);
                    this.state=2;
                }
                break;
        }
        return true;
    }

    /**
     * 发送响应
     */
    private synchronized void sendAck(boolean isSYN, boolean isFIN, int seq, int ack) {
        StpPacket stpPacket = new StpPacket(isSYN, isFIN, seq, ack, null);
        try {
            socket.send(new DatagramPacket(stpPacket.toByteArray(), stpPacket.toByteArray().length, InetAddress.getByName(senderIp), senderPort));

        } catch (IOException e) {
            System.out.println("发送响应失败");
            e.printStackTrace();
        }
    }

    private void writeFile(byte[] data) {
        try {
            FileOutputStream writer = new FileOutputStream(filename, true);
            writer.write(data);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

