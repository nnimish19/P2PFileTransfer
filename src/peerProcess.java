import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Date;
import java.util.HashMap;
import java.util.logging.Logger;
import java.util.*;


public class peerProcess {

    public static int my_peer_id;
    public int listening_port;
    public ServerSocket listening_socket;
    public Thread listening_thread;
    public Thread server_controller;
    public Thread client_controller;
    public static  HashMap<String, String> config_info_map = new HashMap<>();
    public static  HashMap<Integer, String> peer_info_map = new HashMap<>();
    public static  HashMap<Integer, Boolean[]> peer_bitfields = new HashMap<>();
    static Logger myLogger;
    public static int[] my_bitfield;
    //    Setup variables:
    public static int unchoking_interval;
    public static int opt_unchoking_interval;
    public static int piece_cnt=0;
    public static int my_cnt=0;
    public static int max_bitfield_count;

//	hashmap is_connected_peers;	//Fast access: currently connected, unconnected peers.
//	list of connected_peers;	//easy iterate
//	list of unconnected_peers;

//	Hashmap<Integer, Float> download speed;

//	hashmap is_choked		//peers i have choked/unchoked
//	list of choked_peers
//	list of un_choked_peers

//	hashmap<peer,boolean> am_i_choked; 		//if this peer has choked me
//	hashmap<piece_index, int>piece_status: //0 means not downloaded, 1 = currently downloaded, 2 = downloaded
//

//common.cfg
//    NumberOfPreferredNeighbors 2
//    UnchokingInterval 5
//    OptimisticUnchokingInterval 15
//    FileName TheFile.dat
//    FileSize 10000232
//    PieceSize 32768

//PeerInfo.cfg
//    [peer ID] [host name] [listening port] [has file or not]
//	  1001 192.168.0.18 6008 1
//    1002 192.168.0.18 6008 0
//	  1003 192.168.0.18 6008 0

    private static void initialSetup(String my_id) throws IOException {
//        boolean isFirstPeer = false;
        my_peer_id = Integer.parseInt(my_id);
//		read common.cfg and peer_info.cfg
        try {
            File common_config_file = new File("C:/Users/Vivek Gade/Documents/BitTorrent-CN-Project/src/common.cfg");
            FileInputStream commons_file_reader = new FileInputStream(common_config_file);
            BufferedReader commons_buff_reader = new BufferedReader(new InputStreamReader(commons_file_reader));

            File peer_info_file = new File("C:/Users/Vivek Gade/Documents/BitTorrent-CN-Project/src/PeerInfo.cfg");
            FileInputStream peer_file_reader = new FileInputStream(peer_info_file);
            BufferedReader peer_buff_reader = new BufferedReader(new InputStreamReader(peer_file_reader));
            String line = null;

            while((line = commons_buff_reader.readLine()) != null) {
                String[] parts = line.split(" ");
                config_info_map.put(parts[0],parts[1]);
            }
            line = null;
            int total_file_size = Integer.parseInt(config_info_map.get("FileSize"));
            int piece_size = Integer.parseInt(config_info_map.get("PieceSize"));
            if((total_file_size%piece_size)==0){
                piece_cnt = total_file_size/piece_size;
            }else{
                piece_cnt = (total_file_size/piece_size)+1;
            }

            // seting the max bit filed count value
            max_bitfield_count = piece_cnt*2;

            while((line = peer_buff_reader.readLine()) != null) {
                String[] parts = line.split(" ");
                peer_info_map.put(Integer.parseInt(parts[0]),parts[1] +" " + parts[2]);
                Boolean[] bitmap = new Boolean[piece_cnt];
                if(parts[3].equals("1")) Arrays.fill(bitmap, true);
                else Arrays.fill(bitmap, false);

                if(parts[0].equals(my_id)){
                    my_bitfield = new int[piece_size];
                    if(parts[3].equals("1")){
                        Arrays.fill(my_bitfield,2); // setting the bits field to 2 in my bits field because its the server and has the file.
                        my_cnt = max_bitfield_count; // setting the my count to max because i have the file.
                    }
                }
                peer_bitfields.put(Integer.parseInt(parts[0]),bitmap);
            }

            // Always close files.
            commons_buff_reader.close();
            peer_file_reader.close();
        } catch (FileNotFoundException e) {
            System.out.println("Error in reading common or Peer info configuration.");
        }


//      set variables: bitsfield, mybitsfield, unchoking_interval, optimistic_inchoking_interval, piece_cnt, my_cnt
        unchoking_interval = Integer.parseInt(config_info_map.get("UnchokingInterval"));
        opt_unchoking_interval = Integer.parseInt(config_info_map.get("OptimisticUnchokingInterval"));

//		if first peer break the file into pieces and store in subdirectory.
//        	Create subdirectory on the fly with name: peer_1001 in current directory
        new File("peer_" + my_id).mkdir();
    }

    public static void main(String args[]) throws IOException {
        System.out.print("welcome: "+args[0]+"\n");
        myLogger = Logger.getLogger("InfoLogging");

        initialSetup(args[0]);

        peerProcess pp = new peerProcess();
        pp.listening_port = Integer.parseInt(peerProcess.peer_info_map.get(my_peer_id).split(" ")[1]);

        //		start Listening thread, and Server and Client controllers for peerProcess
        try {
            System.out.println("Spawning listening Thread : ");
            pp.listening_socket = new ServerSocket(pp.listening_port);
            pp.listening_thread = new Thread(new ListeningThread( pp.listening_socket, my_peer_id));
            pp.server_controller = new Thread(new ServerController(pp.listening_socket, my_peer_id));
            pp.client_controller = new Thread(new ClientController(pp.listening_socket, my_peer_id));
            //stop the threads
            pp.listening_thread.start();
            pp.server_controller.start();
            pp.client_controller.start();

        } catch (Exception e) {
            consoleLog(e.toString());
            System.exit(0);
        }
    }

    public static Boolean IsSomethingLeftToDownload(){
        //should keep checking infinitely until some peer comes online?
//        my_cnt=-1;	//For testing only. remove this
//        if(my_cnt<piece_cnt)return false;

        if(my_cnt == max_bitfield_count){
            return false;
        }else{
            return true;
        }
    }

    public static Boolean IsAnyoneLeftToDownload(){
        if(my_cnt == 0){
            return false;
        }else{
            return true;
        }
    }

    public static String getTime() {
        Date d = new Date();
        return d.toString();

    }

    public static void consoleLog(String message)
    {
        myLogger.info(getTime() + ": Peer " + message);
        System.out.println(getTime() + ": Peer " + message);
    }

}

//Listener
class ListeningThread implements Runnable{

    private ServerSocket listeningSocket;
    private int peerID;
    Socket remoteSocket;
    Thread sendingThread;

    public ListeningThread(ServerSocket socket, int peerID)
    {
        this.listeningSocket = socket;
        this.peerID = peerID;
    }

    @Override
    public void run(){
        Socket server_cc_socket = null;
        while(peerProcess.IsAnyoneLeftToDownload() || peerProcess.IsSomethingLeftToDownload() ){
//			server socket
//			server accept
            try {
                server_cc_socket = this.listeningSocket.accept();
                //DataInputStream  in_from_client = new DataInputStream (server_cc_socket.getInputStream());
                //DataOutputStream out_to_client = new DataOutputStream(server_cc_socket.getOutputStream());

                ObjectOutputStream out_to_client_obj = new ObjectOutputStream(server_cc_socket.getOutputStream());
                ObjectInputStream in_frm_client_obj = new ObjectInputStream(server_cc_socket.getInputStream());

                int[] client_bitfield = (int[])in_frm_client_obj.readObject();
                System.out.println("Bit field received from the client");
                out_to_client_obj.writeObject(peerProcess.my_bitfield);
                System.out.println("Server bit field sent.");


                //byte[] rec_from_client = new byte[1024];
                //in_from_client.read(rec_from_client, 0, 1024);
                String connection_response =(String) in_frm_client_obj.readObject();
                System.out.println("interested message recieved from the client.");
                switch (connection_response){
                    case "interested":
                        sendingThread = new Thread(new ServerThread(this.listeningSocket,server_cc_socket, this.peerID,in_frm_client_obj,out_to_client_obj));
                        sendingThread.start();
                        break;
                    case "have":
                        break;
                    default:
                        server_cc_socket.close();
                        // drop connection
                        break;
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
//			wait for bitsfield
//			send my_bitfield
//			wait for resonse
//			switch(resonse){
//			case "interested":
            //send_response = decideIfWantToSend()
//				send this above response to user
            //if(send_response == "unchoke")
//					start serverThread		//request-response cycle

//			case "have":
            //send_response = CheckIfYouNeed()	//compare the bitsfield also return the piece index I want(which I am not downloading currently)
//				if(send_response == -1) send "NotInterested"
//			    else(){
//					send "interested"
            //expect "unchoke"
            //				start clientThread	//request-response cycle
//				}
//			case default: drop_connection
//		}
        }
    }
}



//server controller
class ServerController implements Runnable{

    private ServerSocket listeningSocket;
    private int peerID;
    Socket remoteSocket;
    Thread sendingThread;

    public ServerController(ServerSocket socket, int peerID)
    {
        this.listeningSocket = socket;
        this.peerID = peerID;
    }

    @Override
    public void run(){
        while(peerProcess.IsAnyoneLeftToDownload()){
//			select top-k peers: who still need data which this server has
//			select 1 more optimistically

//			send "CHOKE" to all others(who are connected)  > This msg should be receive by client in clientThread.
//			for(every selected peer){
//				send TCP req if not connected already(and exchage bitsfield)
//				send "HAVE" to this peer;
//				wait for response
//				if(response == "INTERESTED"){
//					send "UNCHOKE"
//					start serverThread
//				}
//				else drop_connection
//			}

//			IMP: PAUSE for p=15 seconds now
        }
    }
}



//client controller
class ClientController implements Runnable{

    private ServerSocket listeningSocket;
    private int peerID;
    Socket remoteSocket;
    Thread sendingThread;

    public ClientController(ServerSocket socket, int peerID)
    {
        this.listeningSocket = socket;
        this.peerID = peerID;
    }

    @Override
    public void run(){
        while(peerProcess.IsSomethingLeftToDownload()){

            try {
                for (int peer_id: peerProcess.peer_info_map.keySet()) {
                    if(peer_id < this.peerID){
                        String[] peer_ip = peerProcess.peer_info_map.get(peer_id).split(" ");
                        this.remoteSocket = new Socket(peer_ip[0], Integer.parseInt(peer_ip[1]));
                        ObjectOutputStream out_to_server_obj = new ObjectOutputStream(this.remoteSocket.getOutputStream());
                        ObjectInputStream in_frm_server_obj = new ObjectInputStream(this.remoteSocket.getInputStream());

                        out_to_server_obj.writeObject(peerProcess.my_bitfield);
                        System.out.println("Client bitflied sent.");
                        int[] server_bitfield = (int[])in_frm_server_obj.readObject();
                        System.out.println("Server bitfield recieved.");
                        String message = null;
                        ArrayList req_pieces = compareBitFields(server_bitfield);
                        if(req_pieces.size()!=0){
                            message = "interested";
                            out_to_server_obj.writeObject(message);
                            System.out.println("Interested message sent.");
                            String message_frm_server = (String) in_frm_server_obj.readObject();
                            System.out.println("Received unchoke from the server");
                            System.out.println("received " + message_frm_server);
                            if(message_frm_server.equals("unchoke")){
                                sendingThread = new Thread(new ClientThread(this.listeningSocket,this.peerID,(int)req_pieces.get(0),this.remoteSocket,in_frm_server_obj,out_to_server_obj));
                                sendingThread.start();
                            }else{
                                this.remoteSocket.close();
                            }


                        }else{
                            message = "not interested";
                            out_to_server_obj.writeObject(message);
                            this.remoteSocket.close();
                        }



                    }
                }


            } catch (IOException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }

//			list of peers = select peers who have not "choked me" and are not connected		(also check if they have something to offer? No?)
//			for(every selected peer){
//				send TCP req if not connected already(and exchage bitsfield)
//				send "INTERESTED" to this peer
//				wait for response
//				if(response=="UNCHOKE"){
//					start clientThread
//				}
//				else if "CHOKE"
//					save this status. and drop_connection
//				else drop_connection
//			}
        }
    }

    private ArrayList<Integer> compareBitFields(int[] server_bitfield) {
        ArrayList<Integer> piece_indxs = new ArrayList<>();
        for (int i = 0; i < server_bitfield.length ; i++) {
            if(server_bitfield[i] !=0 && peerProcess.my_bitfield[i] ==0){
                piece_indxs.add(i);
            }
        }
        return piece_indxs;
    }
}



//	serverThread> exchange between 1 client and server only. This connection is currenly active

class ServerThread implements Runnable{

    private Socket live_connection;
    private ServerSocket listeningSocket;
    private int peerID;
    private ObjectOutputStream out_to_client;
    private ObjectInputStream in_frm_client;

    Socket remoteSocket;
    Thread sendingThread;

    public ServerThread(ServerSocket socket, Socket connection ,int peerID,ObjectInputStream input ,ObjectOutputStream output)
    {
        this.listeningSocket = socket;
        this.live_connection = connection;
        this.peerID = peerID;
        this.in_frm_client = input;
        this.out_to_client = output;
    }

    @Override
    public void run(){

        while (true) {
            try {
                //DataInputStream  in_from_client = new DataInputStream (this.listeningSocket.getInputStream());
                //DataOutputStream out_to_client = new DataOutputStream(this.listeningSocket.getOutputStream());


                //if(mssg_from_client.equals("interested")){
                System.out.println("Server thread started.");
                String message ="unchoke";
                this.out_to_client.writeObject(message);
                System.out.println("Sent unchoke to client");

                String piece_num = (String)this.in_frm_client.readObject();
                System.out.println("Received piece number.");
                //System.out.println( piece_num +" is the part");
                String src_file_path = "sample.txt";
                    //
                    //  int i =(int) RecFromClient;
                    //   for(int i=0;i<5;i++)
                    //   {
                String file_name = src_file_path+".part"+ piece_num;
                sendFile(file_name, this.listeningSocket,  this.in_frm_client, this.out_to_client);
                    //   }

            } catch (IOException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
//		private information about the client
        //while(/*(!is_choked(cleint)) &&*/ peerProcess.IsAnyoneLeftToDownload(/*CURRENT CLIENT*/)){	//server could choke the client from SERVER CONTROLLER. stop sending then
//			wait for request
//			if(invalid request) break the connection
//			send the piece requested.
        }
    }

    public static void sendFile(String file_name, ServerSocket server_cc_socket, ObjectInputStream in_from_client , ObjectOutputStream out_to_client) {
            try {

                File file_to_send = new File(file_name);
                byte[] byteArray = new byte[(int) file_to_send.length()];

                FileInputStream file_input_strm = new FileInputStream(file_to_send);
                BufferedInputStream file_buff_strm = new BufferedInputStream(file_input_strm);

                DataInputStream data_strm = new DataInputStream(file_buff_strm);
                data_strm.readFully(byteArray, 0, byteArray.length);
                out_to_client.writeObject(byteArray);
                out_to_client.flush();
                System.out.println("File " + file_name + " sent to Client.");
                out_to_client.close();
            } catch (Exception e) {
                System.err.println("File sending Error!! name : "+ file_name + e);
            }
        }
}


    //	clientThread
//hashmap<piece_index, int>piece_status: //0 means not downloaded, 1 = currently downloaded, 2 = downloaded
class ClientThread implements Runnable{
    private ObjectInputStream in_frm_server;
    private ObjectOutputStream out_to_server;
    private ServerSocket listeningSocket;
    private int piece;
    private int peerID;
    Socket remoteSocket;
    Thread sendingThread;

    public ClientThread(ServerSocket socket, int peerID, int piece_indx, Socket mySocket, ObjectInputStream input, ObjectOutputStream ouput)
    {
        this.listeningSocket = socket;
        this.peerID = peerID;
        this.piece = piece_indx;
        this.remoteSocket = mySocket;
        this.out_to_server = ouput;
        this.in_frm_server = input;
    }

    @Override
    public void run(){
//		private information about the server

        while(peerProcess.IsSomethingLeftToDownload(/*FROM THIS SERVER*/)){

//			piece = check Bitsfield of S; what you need to download from it; (that u are not downloading from other peer also)
//			change download status of piece = 1;
//			start(time)
//			request piece
//			wait for response		//NOTE: THE RESPONSE HERE COULD ALSO BE "CHOKE" or ("REQUEST" if server is also downloading from us). NEED to DIFFERENTIATE THIS.
//			stop(time)
//			updateAvgDS(sever, time)	//download speed
//			if(got_piece_correctly){
//				piece_status[piece] =2;
//				update my_bitfield
//				update my_cnt;
//			}else{
//				if(piece_status[piece]==1)	//CHECK becz: somebody else may have downloaded it correctly and changed status to 2!
//					piece_status[piece] =0
//				drop_connection();
//			}
            try {
                //DataOutputStream out_to_server_obj = new DataOutputStream(this.remoteSocket.getOutputStream());
                //DataInputStream in_frm_server_obj = new DataInputStream(this.remoteSocket.getInputStream());
                //out_to_server_obj.writeObject(this.piece);
                System.out.println("Client thread started.");
                this.out_to_server.writeObject(Integer.toString(this.piece));
                System.out.println("Piece number sent to the server");
                byte[]RecData = (byte[])this.in_frm_server.readObject();
                String SaveFileName = "TextFileFromServer.txt.part"+this.piece;
                OutputStream Fs = new FileOutputStream  (SaveFileName);
                Fs.write(RecData);
                System.out.println("File " + SaveFileName + " received.");
                Fs.close();
                this.in_frm_server.close();
                this.out_to_server.close();
                this.remoteSocket.close();

            } catch (IOException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }


        }
    }
}