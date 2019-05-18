import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.*;

public class Receiver  {
    private String ip;
    private int port;
    private DatagramPacket packet;
    private DatagramSocket socket;
    private byte[] buffer;
    private String filename;
    private int MSS;
    private int header_length;
    /**
     * 记录状态
     * 0： closed
     * 1：syn
     * 2：established
     * 3：fin-wait
     */
    private int state;

    /**
     * @args[0] sender IP Host
     * @args[1] sender port
     * @args[2] file to store
     * @args[3] MSS
     *
     * */

    public static void main(String args[]){
        //
        Receiver receiver=null;
        try {
            receiver=new Receiver(args[0],Integer.parseInt(args[1]),args[2],Integer.parseInt(args[3]));

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


    }


    public Receiver(String ip, int port, String filename, int MSS) throws SocketException, UnknownHostException {
        this.filename = "./resource/" + filename;
        this.MSS = MSS;
        this.port = port;
        this.ip = ip;
        this.socket = new DatagramSocket(this.port, InetAddress.getByName(ip));
        this.buffer=new byte[header_length+MSS];
    }


    public void run(){
        if(handshake()){
            while (true){
                receive();
                if(state==3){
                    killconnection();
                }
            }
        }
    }

    private boolean handshake(){
        return false;
    }

    private synchronized void receive(){

    }

    private boolean killconnection(){
        return false;
    }

    private void writeFile(){
        try {
            FileOutputStream writer = new FileOutputStream(filename, true);
            writer.write(buffer);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}

