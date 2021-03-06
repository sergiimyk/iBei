package iBei.server;

import iBei.aux.Leilao;
import iBei.aux.User;
import iBei.rmi.RMI_Interface;

import java.io.*;
import java.net.*;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
public class TCPServer extends UnicastRemoteObject implements TCP_Interface{
    public static int numero=0;//numero de clientes online
    public static RMI_Interface RMI;
    public static int count=0;
    public static List<Connection> connections;
    private static String [] ipRmi = new String[2];
    public static int currentIp = 1;

    protected TCPServer() throws RemoteException {
        connections = Collections.synchronizedList(new ArrayList<Connection>());
    }

    public static void main(String args[]) {
        String rmiPort = "7000";    // default porta 7000
        if (args.length < 3 || args.length > 4) {
            System.out.println("Usage: <localport> <Primary RMI ip> <RMI Port>");
            System.out.println("Optional: <localport> <Primary RMI ip> <Backup RMI Server ip> <RMI Port>");
            System.exit(0);
        } else if (args.length == 3){   // Backup RMI Server ip = Primary RMI Server ip
            ipRmi[0] = args[1];
            ipRmi[1] = args[1];
            rmiPort = args[2];
        } else if (args.length == 4 && checkIp(args[2])){
            ipRmi[0] = args[1];
            ipRmi[1] = args[2];
            rmiPort = args[3];
        }

        System.out.println("Localport: "+args[0]+"\nPrimary RMI Server: "+args[1]+"\nRMI Port:"+ args[2]);
        String port = args[0];
        int rmi_port = Integer.parseInt(rmiPort);

        try {
            TCPServer.RMI = (RMI_Interface) LocateRegistry.getRegistry(ipRmi[0], rmi_port).lookup("ibei");//RMI
            TCP_Interface tcpserver = new TCPServer();

            int serverPort = Integer.parseInt(port);
            TCPServer.RMI.addTCP(tcpserver);

            InetAddress group = InetAddress.getByName("225.0.0.0");
            MulticastSocket socket = new MulticastSocket(6789);
            socket.joinGroup(group);
            UDPSender udp = new UDPSender(numero, serverPort);

            HashMap <Integer, Integer> info = new HashMap<Integer, Integer>();
            new Thread(){
                public void run(){

                    try {
                        while(true){
                            udp.setNumero(connections.size());
                            byte[] inBuf = new byte[8*1024];
                            DatagramPacket msgIn = new DatagramPacket(inBuf, inBuf.length);
                            socket.receive(msgIn);
                            String rcv = new String(inBuf, 0, msgIn.getLength());
                            checkMsg(rcv, info);
                            System.out.println("Number of clients logged | Received from: " + rcv);
                            //enviar para clientes dados
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }.start();

            new Thread(){
                public void run(){
                    try {
                        while (true) {
                            sendClients(info);
                            Thread.sleep(60000);
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }.start();


            System.out.println("Listening on Port: "+port);
            ServerSocket listenSocket = new ServerSocket(serverPort);

            //loop that accepts clients
            while(true) {
                Socket clientSocket = listenSocket.accept(); // BLOQUEANTE
                System.out.println("CLIENT_SOCKET (created at accept())="+clientSocket);
                numero++;
                Connection c = new Connection(clientSocket, numero);
                //clients.add(c);
                System.out.println(numero);
            }
        } catch(IOException e) {
            System.out.println("Listen: " + e.getMessage());
        } catch(Exception ex){
            System.out.println("NULL ???");
            ex.printStackTrace();
        }
    }

    public static boolean checkIp(String ip) {
        if(ip.matches(".*\\..*\\..*\\..*") && !ip.equals("localhost"))
            return true;
        return false;
    }

    public static void checkMsg(String str, HashMap<Integer, Integer> info){
        String [] fields = str.split(":");
        int i=0;
        for(i=0; i< fields.length;i++){
            fields[i] = fields[i].trim();
        }
        info.put(Integer.parseInt(fields[0]), Integer.parseInt(fields[1]));
    }

    public synchronized static void sendClients(HashMap<Integer, Integer> info){
        int i=0;
        String text = "type: notification_load, server_list: "+info.size()+",";
        for (HashMap.Entry<Integer, Integer> entry : info.entrySet()) {
            Integer key = entry.getKey();
            Integer value = entry.getValue();
            text += " server_"+i+"_hostname: localhost, server_"+i+"_port: " + key+", server_"+i+"_load: "+value;
            i++;

        }
        for(Connection cnt : connections){
            System.out.println(cnt.getUsername());
            cnt.getOut().println(text);
        }
    }


    public synchronized static void RMI_reconnection(){
        try {
            Thread.sleep(2000);
            if (currentIp == 0)
                currentIp = 1;
            else if(currentIp == 1)
                currentIp = 0;
            TCPServer.RMI = (RMI_Interface) LocateRegistry.getRegistry(ipRmi[currentIp],7000).lookup("ibei");


            count = 0;
        } catch (RemoteException | NotBoundException e) {
            count += 2;
            if(count >= 30 && count < 32) {
                System.out.println(ipRmi[currentIp]);
                System.out.println("RMI Servers with problems...");
            }
            TCPServer.RMI_reconnection();
        } catch (InterruptedException e) {
            //e.printStackTrace();
        }
    }

    public void sendMsg(String type, String username, String text, int id_leilao, String author) throws RemoteException{
        for(Connection ctn : connections){
            if(ctn.getUsername().equals(username)) {
                if (type.equals("notification_bid"))
                    ctn.sendMessage("type", type, "id", String.valueOf(id_leilao), "user", author, "amount", text);

                else {
                    ctn.sendMessage("type", type, "id", String.valueOf(id_leilao), "user", author, "text", text);
                }
            }
        }
    }

    public synchronized boolean checkUser(String username) throws RemoteException{
        for(Connection cnt : connections){
            if(cnt.getUsername().equals(username))
                return true;
        }
        return false;
    }


    public synchronized static void addConnections(Connection cnt){
        connections.add(cnt);
    }

}

class UDPSender{
    private AtomicInteger numero;
    private int port;
    public UDPSender(int numero, int tcpPort){

        this.numero = new AtomicInteger(numero);
        this.port = tcpPort;
        try {
            DatagramSocket socket = new DatagramSocket();


            new Thread(){
                public void run() {
                    try {
                        while(true) {
                            InetAddress group = InetAddress.getByName("225.0.0.0");
                            String reply = String.valueOf(tcpPort)+": "+String.valueOf(getNumero());
                            byte[] buf =  reply.getBytes();
                            DatagramPacket msgOut = new DatagramPacket(buf, buf.length, group, 6789);
                            socket.send(msgOut);
                            Thread.sleep(20000);
                        }
                    } catch (InterruptedException | IOException e) {
                        e.printStackTrace();
                    }

                }
            }.start();

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public AtomicInteger getNumero() {
        return numero;
    }

    public void setNumero(int numero) {
        this.numero = new AtomicInteger(numero);
    }
}


// Thread para tratar da comunicação com um cliente
class Connection  extends Thread implements Serializable {
    private PrintWriter out;
    private BufferedReader in= null;
    private int thread_number;
    private boolean logged = false;
    private User u;

    public Connection (Socket socket, int numero) {
        thread_number = numero;
        try{
            out =  new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            this.start();
        } catch(IOException e){
            System.out.println("Connection:" + e.getMessage());
        }
    }


    public String getUsername() {
        return u.username;
    }


    public synchronized void sendMessage(String... args) {
        if (args.length % 2 != 0) { // Se o numero de campos não for par
            System.out.println("\n" + "Error registering message sending fields.");
            return;
        }
        LinkedHashMap<String, String> reply = new LinkedHashMap<String, String>();
        for(int i=0;i<args.length;i+=2)
            reply.put(args[i], args[i+1]);
        String r = reply.toString().replaceAll("=",":");
        out.println(r.substring(1, r.length() - 1));
    }

    // Corre 1 vez por cada cliente, fica sempre no while()
    public void run() {
        try{
            while(true) {
                if(!logged) {//TODO aceder ao User para ver se esta logado em vez de ter uma variavel
                    try {
                        String data= in.readLine();
                        LinkedHashMap<String, String> hashMap = parseData(data);
                        while(hashMap == null) {
                            data = in.readLine();
                            hashMap = parseData(data);
                        }
                        String type = hashMap.get("type");

                        if (type.equals("register")) {
                            register(hashMap);
                        } else if (type.equals("login")) {
                            login(hashMap);
                        } else {
                            sendMessage("type", "status", "logged", "off", "msg", "You must login first!");
                        }
                    } catch (ArrayIndexOutOfBoundsException e) {
                        e.printStackTrace();
                        out.println("You must follow the protocol.");
                    } catch (NullPointerException e) {
                        System.out.println("Enforced end session.");
                        TCPServer.RMI.logoutClient(u.username);
                        this.removeConection(u);
                        break;
                    }
                } else {
                    System.out.println("A espera do user...");
                    System.out.println("Banido: "+u.isBanned());
                    String data= in.readLine();
                    LinkedHashMap<String, String> hashMap = parseData(data);
                    while(hashMap == null) {
                        data = in.readLine();
                        hashMap = parseData(data);
                    }
                    System.out.println("T["+thread_number + "] Received: ");
                    //list elements
                    for (String key : hashMap.keySet()) {

                        String value = hashMap.get(key);
                        System.out.println(key + " : " + value);
                    }

                    this.getType(hashMap);
                    System.out.println("loggado: "+logged);
                }
            }
        } catch(EOFException | NullPointerException | SocketException e){
            System.out.println("The client["+thread_number+"] ("+u.username+") ended the connection.");
            TCPServer.numero--;
            try {
                TCPServer.RMI.logoutClient(u.username);
                System.out.println("User "+u.username+" Enforced end session.");
                this.removeConection(u);
            } catch (RemoteException e1) {
                System.out.println("Error forcing end session: "+u.username);
            }
        } catch(IOException e) {
            System.out.println("IO:" + e);
        }
    }
    //remove connection from Connections array
    public synchronized boolean removeConection(User user){
        for(Connection cnt : TCPServer.connections) {
            if (cnt.getUsername().equals(user.getUsername())) {
                TCPServer.connections.remove(cnt);
                return true;
            }
        }
        return false;
    }

    public LinkedHashMap<String, String> parseData(String data){

        LinkedHashMap<String, String> hashMap = new LinkedHashMap<String, String>();
        try {
            String[] aux = data.split(",");

            for (String field : aux) {
                String[] aux1 = field.trim().split(":", 2);// 2, pq hora:minutos
                aux1[0] = aux1[0].trim();
                aux1[1] = aux1[1].trim();
                hashMap.put(aux1[0], aux1[1]);
            }
        } catch(ArrayIndexOutOfBoundsException e){
            this.out.println("Invalid operation");
            return null;
        }
        return hashMap;
    }


    public void login(LinkedHashMap<String, String> hashMap){
        System.out.println("LOGGING IN...");
        try {
            if(!TCPServer.RMI.login_client(hashMap)) {
                sendMessage("type", "login", "ok", "false");
            } else {
                sendMessage("type", "login", "ok", "true");
                logged = true;
                u = new User(hashMap.get("username"), hashMap.get("password"));
                TCPServer.addConnections(this);
                TCPServer.RMI.checkBidNotf_clients();
                TCPServer.RMI.checkMsgNotf_clients();
            }
        } catch (RemoteException e) {
            e.printStackTrace();
            TCPServer.RMI_reconnection();
            login(hashMap);
            //sendMessage("type", "login", "ok", "false");
        }
    }

    public void register(LinkedHashMap<String, String> data){
        try {
            if(!TCPServer.RMI.register_client(data)) {   // Registo falhado, utilizador já existe
                sendMessage("type", "register", "ok", "false");
            } else {    // Registo bem sucedido
                sendMessage("type", "register", "ok", "true");
            }
        } catch (RemoteException e) {
            //e.printStackTrace();
            TCPServer.RMI_reconnection();
            register(data);
        }
    }
    public void create_auction(LinkedHashMap <String, String> data, String username){
        try {
            System.out.println("CREATE_AUCTION: Title:"+data.get("title")+ "| Description:"+data.get("description"));
            if(TCPServer.RMI.create_auction(data,username) != 0){
                sendMessage("type", "create_auction", "ok", "true");
            } else {
                sendMessage("type", "create_auction","ok","false");
            }
        } catch (RemoteException e) {
            //e.printStackTrace();
            TCPServer.RMI_reconnection();
            create_auction(data, username);
        }
    }
    public void detail_auction(LinkedHashMap<String, String> data){
        try {
            LinkedHashMap<String, String> reply = new LinkedHashMap<String, String>();
            Leilao leilao = null;
            if(data.containsKey("id"))
                leilao = TCPServer.RMI.detail_auction(data);

            int i;

            if (leilao != null) {
                reply.put("type", "detail_auction");
                reply.put("title", leilao.titulo.get(leilao.titulo.size()-1));  // last title
                if(leilao.titulo.size() > 1) {
                    for(i=0;i<leilao.titulo.size()-1;i++) {
                        reply.put("title_"+String.valueOf(i+1),leilao.titulo.get(i));
                    }
                }
                reply.put("description", leilao.descricao.get(leilao.descricao.size()-1));  // last description
                if(leilao.descricao.size() > 1) {
                    for(i=0;i<leilao.descricao.size()-1;i++) {
                        reply.put("description_"+String.valueOf(i+1),leilao.descricao.get(i));
                    }
                }
                reply.put("deadline", leilao.data_termino.toString());
                reply.put("code", leilao.getArtigoId());
                reply.put("messages_count", String.valueOf(leilao.mensagens.size()));
                //mandar mensagens
                for (i = 0; i < leilao.mensagens.size(); i++) {
                    reply.put("messages_" + String.valueOf(i) + "_user", leilao.mensagens.get(i).get("author"));
                    reply.put("messages_" + String.valueOf(i) + "_text", leilao.mensagens.get(i).get("message"));
                }

                reply.put("bids_count", String.valueOf(leilao.licitacoes.size()));
                //mandar licitacoes
                for (i = 0; i < leilao.licitacoes.size(); i++) {
                    reply.put("bids_" + String.valueOf(i) + "_user", leilao.licitacoes.get(i).get("author"));
                    reply.put("bids_" + String.valueOf(i) + "_amount", leilao.licitacoes.get(i).get("bid"));
                }

            } else {
                reply.put("type", "detail_auction");
                reply.put("ok", "false");
            }

            String r = reply.toString().replaceAll("=",":");
            out.println(r.substring(1, r.length() - 1));
        } catch (RemoteException e) {
            //e.printStackTrace();
            TCPServer.RMI_reconnection();
            detail_auction(data);
        }

    }
    public void search_auction(LinkedHashMap<String, String> data) {
        try {
            ArrayList <Leilao> leiloes_encontrados = TCPServer.RMI.search_auction(data);

            LinkedHashMap<String, String> reply = new LinkedHashMap<String, String>();
            int i;
            if (leiloes_encontrados.size() != 0) {
                reply.put("type", "search_auction");
                reply.put("items_count", String.valueOf(leiloes_encontrados.size()));
                for (i = 0; i < leiloes_encontrados.size(); i++) {
                    reply.put("items_" + String.valueOf(i) + "_id", String.valueOf(leiloes_encontrados.get(i).id_leilao));
                    reply.put("items_" + String.valueOf(i) + "_code", String.valueOf(leiloes_encontrados.get(i).artigoId));
                    reply.put("items_" + String.valueOf(i) + "_title", leiloes_encontrados.get(i).titulo.get(leiloes_encontrados.get(i).titulo.size() - 1));//so mostra o titulo mais recente
                }
            } else if(data.containsKey("code")){
                reply.put("type", "search_auction");
                reply.put("items_count", "0");
            } else{
                reply.put("type", "search_auction");
                reply.put("ok", "false");
            }

            String r = reply.toString().replaceAll("=",":");
            out.println(r.substring(1, r.length() - 1));
        } catch (RemoteException e) {
            //e.printStackTrace();
            TCPServer.RMI_reconnection();
            search_auction(data);
        }
    }
    public void my_auctions(LinkedHashMap<String, String> data, String username) {
        try {
            ArrayList <Leilao> leiloes_encontrados = TCPServer.RMI.my_auctions(data,username);

            LinkedHashMap<String, String> reply = new LinkedHashMap<String, String>();
            int i;
            if (leiloes_encontrados.size() != 0) {
                reply.put("type", "my_auctions");
                reply.put("items_count", String.valueOf(leiloes_encontrados.size()));
                for (i = 0; i < leiloes_encontrados.size(); i++) {
                    reply.put("items_" + String.valueOf(i) + "_id", String.valueOf(leiloes_encontrados.get(i).id_leilao));
                    reply.put("items_" + String.valueOf(i) + "_code", String.valueOf(leiloes_encontrados.get(i).artigoId));
                    reply.put("items_" + String.valueOf(i) + "_title", leiloes_encontrados.get(i).titulo.get(leiloes_encontrados.get(i).titulo.size() - 1));//so mostra o titulo mais recente
                }
            } else {
                reply.put("type", "my_auctions");
                reply.put("items_count", "0");
            }

            System.out.println("[ END ]");



            String r = reply.toString().replaceAll("=",":");
            out.println(r.substring(1, r.length() - 1));
        } catch (RemoteException e) {
            //e.printStackTrace();
            TCPServer.RMI_reconnection();
            my_auctions(data, username);
        }


    }
    public void make_bid(LinkedHashMap<String, String> data, String username){
        try {
            Leilao leilao = null;
            if(data.size() == 3 && data.containsKey("id") && data.containsKey("amount"))
                leilao = TCPServer.RMI.make_bid(data, username);

            System.out.println("makebid result: "+leilao);
            if(leilao != null){
                sendMessage("type","bid","ok","true");
                TCPServer.RMI.checkBidNotf_clients();
            }
            else{
                sendMessage("type","bid","ok","false");
            }
        } catch (RemoteException e) {
            TCPServer.RMI_reconnection();
            make_bid(data, username);
        }
    }
    public void write_message(LinkedHashMap<String, String> data, String username){
        try {
            Leilao leilao = null;
            if(data.size() == 3 && data.containsKey("id") && data.containsKey("text"))
                    leilao = TCPServer.RMI.write_message(data, username);
            if(leilao != null){
                sendMessage("type","message","ok","true");
                TCPServer.RMI.checkMsgNotf_clients();
            } else {
                sendMessage("type","message","ok","false");
            }
        } catch (RemoteException e) {
            //e.printStackTrace();
            TCPServer.RMI_reconnection();
            write_message(data, username);
        }
    }

    public void edit_auction(LinkedHashMap<String, String> data, String username){
        try {
            if(!TCPServer.RMI.edit_auction(data, username)) {
                sendMessage("type","edit_auction","ok","false");
            }
            else{sendMessage("type","edit_auction","ok","true");}
        } catch (RemoteException e) {
            TCPServer.RMI_reconnection();
            edit_auction(data, username);
        }
    }
    public void OnlineUsers(){
        LinkedHashMap <String, String> reply = new LinkedHashMap<>();

        try {
            List <User> loggados = TCPServer.RMI.listOnlineUsers();

            System.out.println("[ LIST ONLINE USERS ]");
            reply.put("type", "online_users");
            int users_count = loggados.size();
            reply.put("users_count", String.valueOf(users_count));
            for(int i=0;i<loggados.size();i++) {
                reply.put("users_"+i+"_username", loggados.get(i).username);
            }

            String r = reply.toString().replaceAll("=",":");
            out.println(r.substring(1, r.length() - 1));
        } catch (RemoteException e) {
            TCPServer.RMI_reconnection();
            OnlineUsers();
        }
    }

    public void Logout(String username){
        LinkedHashMap<String, String> reply = new LinkedHashMap<>();
        System.out.println("LOGOUT");
        try {
            reply.put("type","logout");
            if(TCPServer.RMI.logoutClient(username)) {
                reply.put("ok", "true");
                this.removeConection(this.u);
                logged = false;
            }
            else{
                reply.put("ok", "false");
            }
            String r = reply.toString().replaceAll("=", ":");
            out.println(r.substring(1, r.length() - 1));
        } catch (RemoteException e) {
            e.printStackTrace();
            TCPServer.RMI_reconnection();
            Logout(username);
        }
    }

    //ve o tipo de operacao e responde ao cliente conforma o tipo de operacao
    public void getType(LinkedHashMap <String, String> data){
        String username = u.username;
        try{
            switch (data.get("type")) {
                case "create_auction":
                    System.out.println(data);
                    create_auction(data, username);
                    break;
                case "login":    // JA ESTA LOGGADO... false
                    sendMessage("type", "login", "ok", "false");
                    break;
                case "register":    // JA ESTA REGISTADO... false
                    sendMessage("type", "register", "ok", "false");
                    break;
                case "detail_auction":
                    detail_auction(data);
                    break;
                case "search_auction":
                    search_auction(data);
                    break;
                case "my_auctions":
                    my_auctions(data, username);
                    break;
                case "bid":
                    make_bid(data, username);
                    break;
                case "message":
                    write_message(data, username);
                    break;
                case "edit_auction":
                    edit_auction(data, username);
                    break;
                case "online_users":
                    OnlineUsers();
                    break;
                case "logout":
                    Logout(username);
                    break;
                default:
                    System.out.println("Operation not found!");
                    out.println("Operation not found!");
                    break;
                }

        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.println("getType:"+e);
            e.printStackTrace();
            Logout(username);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized PrintWriter getOut() {
        return out;
    }
}
