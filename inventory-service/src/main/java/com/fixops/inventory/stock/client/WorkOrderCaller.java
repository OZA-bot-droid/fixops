package com.fixops.inventory.stock.client;

/**
 * WorkOrder 에 "누가 묻는지"를 함께 전하기 위한 값 (D11).
 *
 * authorization : 사용자가 보낸 "Bearer eyJ..." 그대로 (없으면 null).
 *                 Gateway 경유 호출이나, WorkOrder 가 Asset 처럼 JWT 로 사용자를 확인하게 되면 이 값이 쓰인다.
 * memberId      : 요청한 회원 번호. 지금 WorkOrder API 가 X-Member-Id 헤더를 필수로 받아서 같이 보낸다.
 *
 * 왜 둘 다 보내나
 *   WorkOrder 가 사용자 확인을 X-Member-Id 로 할지, JWT 로 할지 아직 정해지지 않았다 (박천보님 확인 중).
 *   둘 다 보내 두면 어느 쪽으로 정해져도 Inventory 코드를 고치지 않아도 된다.
 */
public record WorkOrderCaller(String authorization, long memberId) {}
