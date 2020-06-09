package client;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.ChaCha20ParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class ChatChat {
    private SecretKey key;

    public ChatChat(String user, String password) throws NoSuchAlgorithmException, InvalidKeySpecException {
        ServeurDB db = new ServeurDB();
        String salt = db.selectSalt(user);
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt.getBytes(), 65536, 256);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");

        byte[] hashedPassword = factory.generateSecret(spec).getEncoded();
        SecretKey key = new SecretKeySpec(hashedPassword, "ChaCha20");
        this.key = key;
    }

    public String encrypt(String plaintext) throws Exception {
        byte[] nonceBytes = new byte[12];
        int counter = 5;

        // Get Cipher Instance
        Cipher cipher = Cipher.getInstance("ChaCha20");

        // Create ChaCha20ParameterSpec
        ChaCha20ParameterSpec paramSpec = new ChaCha20ParameterSpec(nonceBytes, counter);

        // Create SecretKeySpec
        SecretKeySpec keySpec = new SecretKeySpec(key.getEncoded(), "ChaCha20");

        // Initialize Cipher for ENCRYPT_MODE
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, paramSpec);

        // Perform Encryption
        byte[] cipherText = cipher.doFinal(plaintext.getBytes());

        return Base64.getEncoder().encodeToString(cipherText);
    }

    public String decrypt(String cipherText_base64) throws Exception {
        byte[] nonceBytes = new byte[12];
        int counter = 5;

        // Get Cipher Instance
        Cipher cipher = Cipher.getInstance("ChaCha20");

        // Create ChaCha20ParameterSpec
        ChaCha20ParameterSpec paramSpec = new ChaCha20ParameterSpec(nonceBytes, counter);

        // Create SecretKeySpec
        SecretKeySpec keySpec = new SecretKeySpec(key.getEncoded(), "ChaCha20");

        // Initialize Cipher for DECRYPT_MODE
        cipher.init(Cipher.DECRYPT_MODE, keySpec, paramSpec);

        // Perform Decryption
        byte[] cipherText = Base64.getDecoder().decode(cipherText_base64);
        byte[] decryptedText = cipher.doFinal(cipherText);

        return new String(decryptedText);
    }
}
