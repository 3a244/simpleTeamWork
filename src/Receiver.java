import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.*;

public class Receiver {
    private String receiverIp;
    private int receiverPort;
    private String senderIp;
    private int senderPort;
    private DatagramPacket packet;
    private DatagramSocket socket;
    private byte[] Buffer;
    private String filename;
    private int MSS;
    private int header_length;
    private int ack = 1;


    /**
     * @args[0] sender IP Host
     * @args[1] sender port
     * @args[2] file to store
     * @args[3] MSS
     */

    public static void main(String args[]) {
        //
        Receiver receiver = null;
        try {
            receiver = new Receiver(args[0], Integer.parseInt(args[1]), args[2], Integer.parseInt(args[3]));

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


    public Receiver(String ip, int port, String filename, int MSS) throws SocketException, UnknownHostException {
        this.filename = "./resource/" + filename;
        this.MSS = MSS;
        this.senderPort = port;
        this.senderIp = ip;
        this.socket = new DatagramSocket(this.receiverPort, InetAddress.getByName(this.receiverIp));
        this.Buffer = new byte[header_length + MSS];
    }


    public void go() {
        while (handleReceivePacket(receive())) {
        }
    }

    private synchronized StpPacket receive() {
        DatagramPacket datagramPacket = new DatagramPacket(Buffer, Buffer.length);
        try {
            socket.receive(datagramPacket);
        } catch (IOException e) {
            System.out.println("接收错误");
        }
        return new StpPacket(Buffer);
    }

    private synchronized boolean handleReceivePacket(StpPacket stpPacket) {
        if (stpPacket.getData() == null || stpPacket.getData().length == 0) this.ack++;
        else this.ack += stpPacket.getData().length;
        //握手，建立同步
        if (stpPacket.isSYN()) {
            sendAck(true, false, 0, ack);
            return true;
        }
        //接收数据，发送响应
        if ((!stpPacket.isSYN()) && (!stpPacket.isFIN())) {
            sendAck(false, false, 0, ack);
            writeFile();
            return true;
        }
        //发送结束完成响应
        if (stpPacket.isFIN()) {
            sendAck(false, true, 0, ack);
            return false;
        }

        return false;
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

    private boolean killconnection() {
        return false;
    }

    private void writeFile() {
        try {
            FileOutputStream writer = new FileOutputStream(filename, true);
            writer.write(Buffer);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}

