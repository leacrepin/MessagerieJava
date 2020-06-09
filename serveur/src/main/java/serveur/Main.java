package serveur;

public class Main {

    public static void main(String[] args) {

        String host = "127.0.0.1";
        int port = 2345;

        Serveur_multi serveur = new Serveur_multi(host, port);
        serveur.open();

        System.out.println("Serveur initialisé.");
    }
}