package com.fixops.workorder.booking.service;

import com.fixops.workorder.booking.domain.MaintenanceSchedule;
import com.fixops.workorder.booking.domain.ScheduleProposal;
import com.fixops.workorder.booking.repository.MaintenanceScheduleRepository;
import com.fixops.workorder.booking.repository.ScheduleProposalRepository;
import com.fixops.workorder.common.response.BusinessException;
import com.fixops.workorder.common.response.ErrorCode;
import com.fixops.workorder.workorder.domain.WorkOrder;
import com.fixops.workorder.workorder.repository.WorkOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY) //서비스에서 시작한 트랜잭션에 참여하며 독립적인 트랜잭션을 생성
public class BookingLockService {

    private final WorkOrderRepository workOrderRepository;

    private final MaintenanceScheduleRepository scheduleRepository;

    private final ScheduleProposalRepository proposalRepository;


    // WorkOrder 및 MaintenanceSchedule 잠금
    public LockedSchedule lockSchedule(Long scheduleId) {

        if (scheduleId == null || scheduleId <= 0) {

            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }


        // WorkOrder ID만 먼저 조회
        Long workOrderId = scheduleRepository.findWorkOrderIdByScheduleId(scheduleId).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));


        // 1. WorkOrder 잠금
        WorkOrder workOrder = workOrderRepository.findByIdForUpdate(workOrderId).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));


        // 2. MaintenanceSchedule 잠금
        MaintenanceSchedule schedule = scheduleRepository.findByIdForUpdate(scheduleId).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));


        // 참조 관계 검증
        if (!Objects.equals(schedule.getWorkOrderId(), workOrder.getId())) {

            throw new BusinessException(ErrorCode.RESOURCE_VERSION_CONFLICT);
        }


        return new LockedSchedule(workOrder, schedule);
    }


    // 현재 ScheduleProposal 잠금
    public ScheduleProposal lockCurrentProposal(MaintenanceSchedule schedule, Long proposalId) {

        // 요청한 Proposal이 현재 Proposal인지 확인
        schedule.validateCurrentProposal(proposalId);


        // 3. ScheduleProposal 잠금
        return proposalRepository.findByIdForUpdate(proposalId, schedule.getId()).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }


    // WorkOrder와 Schedule을 함께 반환
    public record LockedSchedule(WorkOrder workOrder, MaintenanceSchedule schedule) {
    }
}