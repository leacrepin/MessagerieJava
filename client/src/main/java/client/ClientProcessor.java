package client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import javax.crypto.KeyAgreement;

public class ClientProcessor implements Runnable {

    private Socket sock;
    private ServeurDB db;
    private PrintWriter writer = null;
    private BufferedReader reader = null;
    private BufferedReader clavier = null;
    private ChatChat chat;
    private String user;
    private String password;
    private String secret;

    public ClientProcessor(Socket pSock, ServeurDB db) throws IOException {
        this.sock = pSock;
        this.db = db;
        this.clavier = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("Entrez votre username : ");
        this.user = clavier.readLine();
        System.out.println("Entrez votre password : ");
        this.password = clavier.readLine();
    }

    public void run() {

        try {
            this.writer = new PrintWriter(sock.getOutputStream(), true);
            this.reader = new BufferedReader(new InputStreamReader(sock.getInputStream()));

            //Hello I'm
            writer.println(this.user);

            // DH
            System.out.println("Generate DH keypair ...");
            KeyPairGenerator clientKpairGen = KeyPairGenerator.getInstance("DH");
            clientKpairGen.initialize(2048);
            KeyPair clientKpair = clientKpairGen.generateKeyPair();

            KeyAgreement clientKeyAgree = KeyAgreement.getInstance("DH");
            clientKeyAgree.init(clientKpair.getPrivate());

            byte[] clientPubKeyEnc = clientKpair.getPublic().getEncoded();

            X509EncodedKeySpec x509KeySpec = new X509EncodedKeySpec(clientPubKeyEnc);

            String to_send = Base64.getEncoder().encodeToString(clientPubKeyEnc);
            writer.println(to_send);
            String to_decode = reader.readLine();
            byte[] serveurPubKeyEnc = Base64.getDecoder().decode(to_decode);

            KeyFactory clientKeyFac = KeyFactory.getInstance("DH");
            x509KeySpec = new X509EncodedKeySpec(serveurPubKeyEnc);
            PublicKey serveurPubKey = clientKeyFac.generatePublic(x509KeySpec);
            System.out.println("Client: Execute PHASE1 ...");
            clientKeyAgree.doPhase(serveurPubKey, true);

            byte[] clientSharedSecret = new byte[256];
            clientSharedSecret = clientKeyAgree.generateSecret();
            this.secret = Utils.toHexString(clientSharedSecret);
            System.out.println("Client secret: " + Utils.toHexString(clientSharedSecret));

            // Challenge
            String challenge = reader.readLine();
            System.out.println("Challenge Received");
            ChallengeAccepted challengeAccepted = new ChallengeAccepted(challenge, this.user, this.password);
            writer.println(challengeAccepted.getChallengeHashed());
            String challenge_completed = reader.readLine();
            if (challenge_completed.equals("Fail")) {
                System.out.println("Challenge Failed. Closing connection");
                stopConnection();
                return;
            } else {
                System.out.println("Challenge Completed");
            }

            // Encryption
            this.chat = new ChatChat(this.user, this.secret);
        } catch (Exception e) {
            e.printStackTrace();
        }

        while (!sock.isClosed()) {
            try {
                if (sendMessage())
                    return;
                if (readMessage())
                    return;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public boolean sendMessage() throws Exception {
        System.out.println("Enter your message : ");
        String str = this.clavier.readLine();
        String encrypt = chat.encrypt(str);
        this.db.addMessage("Client", encrypt);
        writer.println(encrypt);
        if (str.equals("END")) {
            stopConnection();
            return true;
        }
        return false;
    }

    public boolean readMessage() throws Exception {
        String crypted = reader.readLine();
        this.db.addMessage("Serveur", crypted);
        String resp = chat.decrypt(crypted);
        System.out.println("Serveur : " + resp);
        if (resp.equals("END")) {
            stopConnection();
            return true;
        }
        return false;
    }

    public void stopConnection() throws IOException {
        reader.close();
        writer.close();
        sock.close();
        clavier.close();
        System.out.println("Connection ended.");
    }

}