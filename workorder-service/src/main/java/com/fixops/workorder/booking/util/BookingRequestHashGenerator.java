package com.fixops.workorder.booking.util;

import com.fixops.workorder.booking.dto.ScheduleSaveDTO;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import com.fixops.workorder.booking.dto.ScheduleProposalSaveDTO;

@Component
public class BookingRequestHashGenerator {


    // 최초 예약 요청의 해시 생성
    public String generateScheduleHash(ScheduleSaveDTO dto) {

        String payload = encode(String.valueOf(dto.getWorkOrderId()), dto.getStartAt().toString(), dto.getEndAt().toString(), normalizeReason(dto.getReason()));

        return sha256(payload);
    }


    // 선택 입력값 정규화
    public String normalizeReason(String reason) {

        if (reason == null || reason.isBlank()) {
            return null;
        }

        return reason.trim();
    }


    // 필드별 길이를 포함하여 하나의 문자열 생성
    private String encode(String... values) {

        StringBuilder result = new StringBuilder();

        for (String value : values) {

            if (value == null) {

                result.append("-1:");

            } else {

                result.append(value.length()).append(":").append(value);
            }
        }

        return result.toString();
    }


    // SHA-256 해시 생성
    private String sha256(String payload) {

        try {

            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException e) {

            throw new IllegalStateException("SHA-256 해시 생성 실패", e);
        }
    }

    // 기술자 대체 제안 및 요청자 재제안
    public String generateProposalHash(Long scheduleId, ScheduleProposalSaveDTO dto) {

        // Proposal ID를 Hash에 추가된 이유
        // 같은 예약이더라도 수락 대상 Proposal이 서로 다르기 때문에
        // Schedule ID와 Proposal ID를 Hash에 모두 포함해야 동일 Key로 다른 시간 제안을 수락하는 행위를 방지할 수 있음
        String payload = encode(String.valueOf(scheduleId), String.valueOf(dto.getCurrentProposalId()), dto.getStartAt().toString(),
                                                                        dto.getEndAt().toString(), normalizeReason(dto.getReason()));

        return sha256(payload);
    }


    // 현재 Proposal 수락
    public String generateAcceptHash(Long scheduleId, Long proposalId) {

        String payload = encode(String.valueOf(scheduleId), String.valueOf(proposalId));

        return sha256(payload);
    }


    // 현재 Proposal 거절
    public String generateRejectHash(Long scheduleId, Long proposalId, String reason) {

        String payload = encode(String.valueOf(scheduleId), String.valueOf(proposalId), normalizeReason(reason));

        return sha256(payload);
    }
}