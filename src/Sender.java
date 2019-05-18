import java.net.*;

public class Sender implements Runnable{

    private String ip;
    private String filename;
    private int port;
    private int MSS;
    private int MWS;
    private int header_length;
    private byte[] buffer;

    /**
     * 记录状态
     * 0： closed
     * 1：syn-sent
     * 2：established
     * 3：fin-wait
     */
    private int state;

    private DatagramPacket packet;
    private DatagramSocket socket;

    public Sender(String ip, int port, String filename, int MSS) throws SocketException, UnknownHostException {
        this.filename = "./resource/" + filename;
        this.MSS = MSS;
        this.MWS = 5 * MSS;
        this.port = port;
        this.ip = ip;
        this.socket = new DatagramSocket(this.port, InetAddress.getByName(ip));
        this.buffer=new byte[header_length+MSS];
    }

    /**
     * @args[0] receiver IP Host
     * @args[1] receiver port
     * @args[2] file to send
     * @args[3] MSS
     *
     * */
    public static void main(String args[]){
        //
        Sender sender=null;
        try {
            sender=new Sender(args[0],Integer.parseInt(args[1]),args[2],Integer.parseInt(args[3]));

        }catch (SocketException e){
            System.out.println("socket建立失败");
            return;
        }catch (UnknownHostException e){
            System.out.println("无效的IP地址");
            return;
        }catch (ArrayIndexOutOfBoundsException e){
            System.out.println("参数数量不符合要求");
            return;
        }catch (NumberFormatException e){
            System.out.println("参数格式不符合要求");
            return;
        }
        System.out.println("socket初始化成功");
        Thread s=new Thread(sender);
        s.start();

    }
    public void start(){
        Thread t=new Thread(this);
        t.run();

        handshake();
        transport();
        killconnection();
    }

    /**
     * 接收报文
     */
    public void run(){

    }


    private boolean handshake(){
        return false;
    }

    private boolean transport(){
        /*
         *
         *
         * */
        return false;
    }

    /**
     * 报文发送封装
     */
    private synchronized void send(){

    }

    private boolean killconnection(){
        return false;
    }

}
