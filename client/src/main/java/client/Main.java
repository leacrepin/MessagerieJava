package client;

import java.net.Socket;

public class Main {

    public static void main(String[] args) throws Exception {
        Socket client = new Socket("127.0.0.1", 2345);
        ServeurDB db = new ServeurDB();
        Thread t = new Thread(new ClientProcessor(client, db));
        t.start();
    }

}