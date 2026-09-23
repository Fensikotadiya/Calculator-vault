package com.privatecalc.vault;

import android.content.Context;
import android.net.Uri;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.*;

final class VaultStore {
    private static final String CONTACTS = "contacts.store", CONTACTS_ID = "contacts-v1", CONTACTS_MIME = "text/x-vault-contacts";
    private final File directory;
    private final Context context;
    VaultStore(Context context) {
        this.context = context.getApplicationContext();
        directory = new File(context.getNoBackupFilesDir(), "vault");
        directory.mkdirs();
    }
    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (!store.containsAlias("vault-media-v1")) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder("vault-media-v1", KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());
            generator.generateKey();
        }
        return (SecretKey) store.getKey("vault-media-v1", null);
    }
    File[] list() {
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".vault"));
        if (files == null) return new File[0];
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        return files;
    }
    // Each chunk is authenticated with the file identifier and its position.
    // An authenticated empty terminal chunk detects truncation at chunk boundaries.
    void importMedia(Uri uri, String mime) throws Exception {
        String id = UUID.randomUUID().toString();
        File temporary = new File(directory, id + ".partial");
        SecretKey secret = key();
        try (InputStream input = context.getContentResolver().openInputStream(uri);
             DataOutputStream output = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(temporary)))) {
            if (input == null) throw new IOException("Cannot read selected file");
            EncryptedMedia.encrypt(input, output, secret, id, mime);
        } catch (Exception e) { temporary.delete(); throw e; }
        if (!temporary.renameTo(new File(directory, id + ".vault"))) { temporary.delete(); throw new IOException("Cannot save media"); }
    }
    // Contacts live in one small file beside the media, in the same authenticated format and under the same key.
    // Its name does not end in .vault, so list() never returns it as a media item.
    ArrayList<Contacts.Entry> loadContacts() throws Exception {
        File file = new File(directory, CONTACTS);
        if (!file.exists()) return new ArrayList<>();
        ByteArrayOutputStream plain = new ByteArrayOutputStream();
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            EncryptedMedia.decrypt(input, plain, key(), CONTACTS_ID);
        }
        byte[] bytes = plain.toByteArray();
        try { return Contacts.decode(new String(bytes, StandardCharsets.UTF_8)); } finally { Arrays.fill(bytes, (byte) 0); }
    }
    /** Writes the whole list to a temporary file first, so a failed write leaves the saved contacts untouched. */
    void saveContacts(List<Contacts.Entry> entries) throws Exception {
        byte[] bytes = Contacts.encode(entries).getBytes(StandardCharsets.UTF_8);
        File temporary = new File(directory, CONTACTS + ".partial");
        try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(temporary)))) {
            EncryptedMedia.encrypt(new ByteArrayInputStream(bytes), output, key(), CONTACTS_ID, CONTACTS_MIME);
        } catch (Exception e) { temporary.delete(); throw e; }
        finally { Arrays.fill(bytes, (byte) 0); }
        if (!temporary.renameTo(new File(directory, CONTACTS))) { temporary.delete(); throw new IOException("Cannot save contacts"); }
    }
    String mime(File file) throws IOException {
        try (DataInputStream input = new DataInputStream(new FileInputStream(file))) { return input.readUTF(); }
    }
    void decrypt(File file, OutputStream output) throws Exception {
        String id = file.getName().replace(".vault", "");
        SecretKey secret = key();
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            EncryptedMedia.decrypt(input, output, secret, id);
        }
    }
}
