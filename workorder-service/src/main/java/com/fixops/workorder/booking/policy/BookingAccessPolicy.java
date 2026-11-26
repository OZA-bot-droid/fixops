package com.fixops.workorder.booking.policy;

import com.fixops.workorder.common.response.BusinessException;
import com.fixops.workorder.common.response.ErrorCode;
import com.fixops.workorder.report.repository.ReportRepository;
import com.fixops.workorder.workorder.domain.WorkOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;
import com.fixops.workorder.booking.domain.MaintenanceSchedule;
import com.fixops.workorder.booking.domain.ScheduleProposal;

@Component
@RequiredArgsConstructor
public class BookingAccessPolicy {

    private final ReportRepository reportRepository;


    // 인증 사용자 ID 검증
    public void validateActor(Long userId) {

        if (userId == null || userId <= 0) {

            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
    }


    // 최초 예약 요청 권한 확인
    public void validateCreateSchedule(WorkOrder workOrder, Long userId) {

        validateActor(userId);


        // WorkOrder 생성 관리자 확인
        boolean creatorAdmin = Objects.equals(workOrder.getReportedBy(), userId);


        // 연결된 신고의 최초 신고자 확인
        boolean reportOwner = reportRepository.findByWorkOrderId(workOrder.getId()).map(report ->
                                                        Objects.equals(report.getReportedBy(), userId)).orElse(false);


        // 현재 DB에서 확인 가능한 요청자만 허용
        if (!creatorAdmin && !reportOwner) {

            throw new BusinessException(ErrorCode.ORG_ACCESS_DENIED);
        }


        // TODO:
        // Member 연동 이후 실제 USER / ADMIN 역할 검증
        // 조직 및 단지 접근 권한 검증
        // 입주민 세대 소속 검증
        // 관리자 관리 범위 검증
    }

    // 해당 예약의 일정 협의 참여자(예약의 최초 요청자와 배정된 기술자)인지 확인
    // 관리자라고 하더라도 자신이 시작하지 않은 예약을 임의로 수락하거나 시간 제안 못하게 막음
    public void validateNegotiationParticipant(MaintenanceSchedule schedule, Long userId) {

        validateActor(userId);


        boolean requester = Objects.equals(schedule.getRequestedBy(), userId);

        boolean technician = Objects.equals(schedule.getTechnicianId(), userId);


        if (!requester && !technician) {

            throw new BusinessException(ErrorCode.ORG_ACCESS_DENIED);
        }
    }

    // 현재 Proposal에 응답할 권한 검증
    // 현재 순서에 실제로 응답해야 하는 사용자인지 확인
    public void validateProposalResponder(MaintenanceSchedule schedule, ScheduleProposal proposal, Long userId) {

        validateNegotiationParticipant(schedule, userId);


        Long expectedResponderId = switch (schedule.getStatus()) {

            case PENDING -> schedule.getTechnicianId();

            case COUNTER_PROPOSED -> schedule.getRequestedBy();

            default -> throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        };


        if (!Objects.equals(expectedResponderId, userId)) {

            throw new BusinessException(ErrorCode.ORG_ACCESS_DENIED);
        }


        // 자기 자신이 제안한 시간에는 응답 불가
        if (Objects.equals(proposal.getProposedBy(), userId)) {

            throw new BusinessException(ErrorCode.ORG_ACCESS_DENIED);
        }
    }

    // 예약 취소 권한 검증
    public void validateCancelSchedule(WorkOrder workOrder, MaintenanceSchedule schedule, Long userId) {

        validateActor(userId);


        // 최초 예약 요청자
        boolean requester = Objects.equals(schedule.getRequestedBy(), userId);


        // 해당 예약 및 현재 WorkOrder의 담당 기술자
        boolean technician = Objects.equals(schedule.getTechnicianId(), userId) && Objects.equals(workOrder.getAssignedTo(), userId);


        // WorkOrder 생성 관리자
        boolean creatorAdmin = Objects.equals(workOrder.getReportedBy(), userId);


        if (!requester && !technician && !creatorAdmin) {

            throw new BusinessException(ErrorCode.ORG_ACCESS_DENIED);
        }


        // TODO: Member 서비스 연동 이후
        // 실제 관리자 역할 및 조직·단지 관리 권한 검증



    }

    // 방문 완료 권한 검증
    public void validateCompleteSchedule(WorkOrder workOrder, MaintenanceSchedule schedule, Long userId) {

        validateActor(userId);

        boolean scheduleTechnician = Objects.equals(schedule.getTechnicianId(), userId);

        boolean assignedTechnician = Objects.equals(workOrder.getAssignedTo(), userId);

        if (!scheduleTechnician || !assignedTechnician) {

            throw new BusinessException(ErrorCode.ORG_ACCESS_DENIED);
        }
    }
}