import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.PosixFilePermission;
import java.security.*;
import java.util.*;

/** Run from repository root: java scripts/GenerateJwtKeys.java [output-dir] */
public class GenerateJwtKeys {
    static String pem(String type, byte[] der) {
        return "-----BEGIN " + type + "-----\n"
            + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(der)
            + "\n-----END " + type + "-----\n";
    }
    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args.length > 0 ? args[0] : "local-secrets/jwt");
        Path priv = dir.resolve("private_key.pem");
        Path pub = dir.resolve("public_key.pem");
        Files.createDirectories(dir);
        if (Files.exists(priv) || Files.exists(pub)) throw new IllegalStateException("JWT key already exists; refusing overwrite");
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072, new SecureRandom());
        KeyPair pair = generator.generateKeyPair();
        try {
            Files.writeString(priv, pem("PRIVATE KEY", pair.getPrivate().getEncoded()), StandardCharsets.US_ASCII,
                StandardOpenOption.CREATE_NEW);
            Files.writeString(pub, pem("PUBLIC KEY", pair.getPublic().getEncoded()), StandardCharsets.US_ASCII,
                StandardOpenOption.CREATE_NEW);
            try { Files.setPosixFilePermissions(priv, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)); }
            catch (UnsupportedOperationException ignored) { /* Windows: configure ACL separately */ }
            System.out.println("Generated RSA-3072 PKCS#8 private and X.509 public PEM files in " + dir);
        } catch (Exception ex) { Files.deleteIfExists(priv); Files.deleteIfExists(pub); throw ex; }
    }
}
