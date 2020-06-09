package serveur;

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
import javax.crypto.interfaces.DHPublicKey;
import javax.crypto.spec.DHParameterSpec;

public class ClientProcessor implements Runnable {

    private Socket sock;
    private ServeurDB db;
    private PrintWriter writer = null;
    private BufferedReader reader = null;
    private BufferedReader clavier = null;
    private ChatChat chat;
    private String user;
    private String secret;

    public ClientProcessor(Socket pSock, ServeurDB db) throws IOException {
        this.sock = pSock;
        this.db = db;
        this.clavier = new BufferedReader(new InputStreamReader(System.in));
    }

    public void run() {
        try {
            this.writer = new PrintWriter(sock.getOutputStream(), true);
            this.reader = new BufferedReader(new InputStreamReader(sock.getInputStream()));

            //Le client se connecte
            this.user = reader.readLine();

            // DH

            String to_decode = reader.readLine();
            byte[] clientPubKeyEnc = Base64.getDecoder().decode(to_decode);

            KeyFactory serveurKeyFac = KeyFactory.getInstance("DH");
            X509EncodedKeySpec x509KeySpec = new X509EncodedKeySpec(clientPubKeyEnc);

            PublicKey clientPubKey = serveurKeyFac.generatePublic(x509KeySpec);

            DHParameterSpec dhParamFromClientPubKey = ((DHPublicKey) clientPubKey).getParams();

            // Serveur creates his own DH key pair
            System.out.println("Serveur: Generate DH keypair ...");
            KeyPairGenerator serveurKpairGen = KeyPairGenerator.getInstance("DH");
            serveurKpairGen.initialize(dhParamFromClientPubKey);
            KeyPair serveurKpair = serveurKpairGen.generateKeyPair();

            // Serveur creates and initializes his DH KeyAgreement object
            System.out.println("Serveur: Initialization ...");
            KeyAgreement serveurKeyAgree = KeyAgreement.getInstance("DH");
            serveurKeyAgree.init(serveurKpair.getPrivate());

            // Serveur encodes his public key, and sends it over to Client.
            byte[] serveurPubKeyEnc = serveurKpair.getPublic().getEncoded();
            String to_send = Base64.getEncoder().encodeToString(serveurPubKeyEnc);
            writer.println(to_send);

            System.out.println("Serveur: Execute PHASE1 ...");
            serveurKeyAgree.doPhase(clientPubKey, true);

            byte[] serveurSharedSecret = new byte[256];

            serveurKeyAgree.generateSecret(serveurSharedSecret, 0);
            this.secret = Utils.toHexString(serveurSharedSecret);
            System.out.println("Serveur secret: " + Utils.toHexString(serveurSharedSecret));

            // Challenge
            System.out.println("Challenge Sent");
            Challenge challenge = new Challenge(this.user);
            writer.println(challenge.getChallenge());
            String hashChallenge = reader.readLine();
            if (!challenge.compareChallenge(hashChallenge)) {
                writer.println("Fail");
                System.out.println("Challenge Failed. Closing connection");
                stopConnection();
                return;
            } else {
                writer.println("OK");
                System.out.println("Challenge Completed");
            }

            // Encryption
            this.chat = new ChatChat(this.user, this.secret);
        } catch (Exception e) {
            e.printStackTrace();
        }

        while (!sock.isClosed()) {
            try {
                if (readMessage())
                    return;
                if (sendMessage())
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
        this.db.addMessage("Serveur", encrypt);
        writer.println(encrypt);
        if (str.equals("END")) {
            stopConnection();
            return true;
        }
        return false;
    }

    public boolean readMessage() throws Exception {
        String crypted = reader.readLine();
        this.db.addMessage(this.user, crypted);
        String resp = chat.decrypt(crypted);
        System.out.println(this.user+" : " + resp);
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