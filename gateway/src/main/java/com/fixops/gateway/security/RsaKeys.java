package com.fixops.gateway.security;
import java.nio.file.*; import java.security.*; import java.security.interfaces.*; import java.security.spec.*; import java.util.Base64;
public final class RsaKeys {
 private RsaKeys(){}
 private static byte[] read(String path) throws Exception {
  String pem=Files.readString(Path.of(path));
  String b64=pem.replaceAll("-----BEGIN [^-]+-----", "").replaceAll("-----END [^-]+-----", "").replaceAll("\\s", "");
  return Base64.getDecoder().decode(b64);
 }
 public static RSAPrivateKey privateKey(String path) throws Exception {return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(read(path)));}
 public static RSAPublicKey publicKey(String path) throws Exception {return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(read(path)));}
}
