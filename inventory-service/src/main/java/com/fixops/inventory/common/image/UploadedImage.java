package com.fixops.inventory.common.image;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import org.springframework.web.multipart.MultipartFile;

/**
 * 올린 사진 검사 (부품 요청 사진, 부품 사진이 함께 쓴다).
 *   - 장당 5MB 이하 (DB 의 CHECK 제약과 같은 값)
 *   - JPEG / PNG / WEBP 만. 브라우저가 보낸 Content-Type 을 믿지 않고 파일 앞부분(매직 바이트)으로 다시 확인한다
 *     (이름만 .jpg 로 바꾼 다른 파일을 막는다)
 * 틀리면 IllegalArgumentException → 400 INVALID_INPUT
 */
public final class UploadedImage {

    public static final int MAX_BYTES = 5 * 1024 * 1024;

    private UploadedImage() {}

    /** 검사하고 실제 형식(image/jpeg 등)을 돌려준다 */
    public static String validate(MultipartFile f) {
        if (f.isEmpty() || f.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("사진은 한 장에 5MB 이하여야 합니다.");
        }
        String type = detectType(bytes(f));
        if (type == null) {
            throw new IllegalArgumentException("JPEG, PNG, WEBP 사진만 첨부할 수 있습니다.");
        }
        return type;
    }

    // 파일 앞부분으로 실제 형식 판별 (JPEG: FF D8 FF / PNG: 89 50 4E 47 / WEBP: "RIFF" .... "WEBP")
    public static String detectType(byte[] d) {
        if (d.length >= 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (d.length >= 4 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') {
            return "image/png";
        }
        if (d.length >= 12 && Arrays.equals(Arrays.copyOfRange(d, 0, 4), "RIFF".getBytes())
                && Arrays.equals(Arrays.copyOfRange(d, 8, 12), "WEBP".getBytes())) {
            return "image/webp";
        }
        return null;
    }

    public static byte[] bytes(MultipartFile f) {
        try {
            return f.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 파일 이름에서 경로·제어 문자를 빼고 길이를 자른다 (화면에 표시만 하는 값) */
    public static String safeName(String name) {
        String n = name == null ? "image" : name.replaceAll("[\\\\/\\p{Cntrl}]", "_").trim();
        if (n.isEmpty()) {
            n = "image";
        }
        return n.length() > 200 ? n.substring(n.length() - 200) : n;
    }
}
