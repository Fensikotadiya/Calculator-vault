package com.privatecalc.vault;

import java.io.*;
import java.util.*;
import javax.crypto.*;

public class EncryptedMediaTest {
    public static void main(String[] args) throws Exception {
        KeyGenerator generator=KeyGenerator.getInstance("AES"); generator.init(256); SecretKey key=generator.generateKey();
        for(int size:new int[]{0,17,1024*1024,1024*1024+123,3*1024*1024}) {
            byte[] original=new byte[size]; new Random(size).nextBytes(original);
            ByteArrayOutputStream encoded=new ByteArrayOutputStream();
            EncryptedMedia.encrypt(new ByteArrayInputStream(original),new DataOutputStream(encoded),key,"test-id","video/mp4");
            byte[] ciphertext=encoded.toByteArray();
            if(!Arrays.equals(original,decode(ciphertext,key,"test-id"))) throw new AssertionError("Roundtrip failed");
            reject(Arrays.copyOf(ciphertext,ciphertext.length-1),key,"test-id");
            reject(Arrays.copyOf(ciphertext,ciphertext.length-32),key,"test-id");
            reject(Arrays.copyOf(ciphertext,ciphertext.length+1),key,"test-id");
            reject(ciphertext,key,"another-id");
            reject(ciphertext,generator.generateKey(),"test-id");
            byte[] tampered=ciphertext.clone(); tampered[tampered.length-1]^=1; reject(tampered,key,"test-id");
            byte[] metadata=ciphertext.clone(); metadata[2]^=1; reject(metadata,key,"test-id");
        }
        System.out.println("Encrypted media: 40 roundtrip and tamper checks passed");
    }
    private static byte[] decode(byte[] value,SecretKey key,String id) throws Exception {
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        EncryptedMedia.decrypt(new DataInputStream(new ByteArrayInputStream(value)),output,key,id); return output.toByteArray();
    }
    private static void reject(byte[] value,SecretKey key,String id) throws Exception {
        try { decode(value,key,id); } catch(Exception expected) { return; }
        throw new AssertionError("Accepted corrupt encrypted media");
    }
}
