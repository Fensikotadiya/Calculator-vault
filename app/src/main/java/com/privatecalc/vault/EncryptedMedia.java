package com.privatecalc.vault;

import java.io.*;
import java.nio.charset.StandardCharsets;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** Authenticated, bounded-memory media file format. */
final class EncryptedMedia {
    private static final int CHUNK = 1024 * 1024;
    static void encrypt(InputStream input, DataOutputStream output, SecretKey key, String id, String mime) throws Exception {
        output.writeUTF(mime);
        byte[] buffer = new byte[CHUNK];
        int index = 0;
        while (true) {
            int count = 0, read;
            while (count < buffer.length && (read = input.read(buffer, count, buffer.length - count)) != -1) count += read;
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key);
            cipher.updateAAD(aad(id, mime, index++));
            byte[] encrypted = cipher.doFinal(buffer, 0, count);
            output.write(cipher.getIV()); output.writeInt(encrypted.length); output.write(encrypted);
            if (count == 0) break;
        }
    }
    static void decrypt(DataInputStream input, OutputStream output, SecretKey key, String id) throws Exception {
        String mime = input.readUTF();
        int index = 0;
        while (true) {
            byte[] iv = new byte[12]; input.readFully(iv);
            int size = input.readInt();
            if (size < 16 || size > CHUNK + 16) throw new IOException("Invalid encrypted file");
            byte[] encrypted = new byte[size]; input.readFully(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            cipher.updateAAD(aad(id, mime, index++));
            byte[] plain = cipher.doFinal(encrypted);
            if (plain.length == 0) { if (input.read() != -1) throw new IOException("Unexpected data"); break; }
            output.write(plain);
        }
    }
    private static byte[] aad(String id, String mime, int index) { return (id + ":" + mime + ":" + index).getBytes(StandardCharsets.UTF_8); }
}
