import java.net.*;

public class Sender implements Runnable{


    PortMonitor portMonitor=null;

    String ip;
    String filename;
    int port;
    int MSS;
    int MWS;
    int header_length;
    boolean hasFIN;
    boolean hasSYN;

    DatagramPacket packet;
    DatagramSocket socket;

    void transporter(String ip,int port,String filename,int MSS){
        this.filename="./resource/"+filename;
        this.MSS=MSS;
        this.MWS=5*MSS;
        this.port=port;
        this.ip=ip;

        try {
            this.socket=new DatagramSocket(this.port, InetAddress.getByName(ip));
        } catch (SocketException e) {
            System.out.print("fail to creat socket.");
            e.printStackTrace();
        } catch (UnknownHostException e) {
            System.out.print("Invalid Hostname.");
            e.printStackTrace();
        }


    }


    boolean handshake(){
        return false;
    }

    boolean transport(){
        /*
         *
         *
         * */
        return false;
    }

    synchronized void send(){

    }

    boolean killconnection(){
        return false;
    }

    /**
     * @args[0] receiver IP Host
     * @args[1] receiver port
     * @args[2] file to send
     * @args[3] MSS
     *
     * */
    public static void main(String args[]){

        Sender sender=new Sender();
        if(args.length==4){
            try {
                sender.portMonitor(new DatagramSocket(Integer.parseInt(args[0]),InetAddress.getByName(args[1])));
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }catch (SocketException e) {
                e.printStackTrace();
            }
            sender.transporter(args[0],Integer.parseInt(args[1]),args[2],Integer.parseInt(args[3]));

            Thread thread=new Thread(sender);
            thread.start();

            if(sender.handshake()){
                if(sender.transport()){
                    if(sender.killconnection()){
                        System.out.println("end.");
                    }
                }
            }

        }
        else{
            System.out.println("Invalid arguments to start sender.");
        }
    }

    void portMonitor(DatagramSocket socket){
        this.socket=socket;
    }
    public void run(){

    }

}
