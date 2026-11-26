const messages = {
  EMAIL_VERIFICATION_REQUIRED: "이메일 인증을 먼저 완료해 주세요.",
  INVALID_CREDENTIALS: "이메일 또는 비밀번호를 확인해 주세요.",
  EMAIL_ALREADY_EXISTS: "이미 등록된 이메일입니다.",
  INVALID_INPUT: "입력값을 확인해 주세요.",
  RATE_LIMITED: "요청 횟수 제한에 도달했습니다. 잠시 후 다시 시도해 주세요.",
  ORG_ACCESS_DENIED: "해당 조직 또는 단지에 접근할 권한이 없습니다.",
  PLATFORM_ADMIN_REQUIRED: "플랫폼 관리자 권한이 필요합니다.",
  BUILDING_MANAGEMENT_VERIFICATION_REQUIRED: "단지 관리권 심사를 먼저 완료해 주세요.",
  COMPLEX_ADDRESS_REVIEW_REQUIRED: "동일 주소의 다른 단지를 확인해 주세요.",
  COMPLEX_ALREADY_REGISTERED: "이미 관리 중인 단지입니다.",
  ROSTER_IMPORT_CONFLICT: "명부 상태가 변경되었거나 누락 확인이 필요합니다.",
  ROSTER_VALIDATION_FAILED: "명부 검증 결과를 확인해 주세요.",
  PAYLOAD_TOO_LARGE: "파일 크기 제한을 초과했습니다.",
  DEPENDENCY_UNAVAILABLE: "연결된 서비스를 이용할 수 없습니다. 잠시 후 다시 시도해 주세요.",
  SESSION_STORE_UNAVAILABLE: "인증 서버를 이용할 수 없습니다. 잠시 후 다시 시도해 주세요.",
  INTERNAL_ERROR: "서버 오류가 발생했습니다.",
};
export function errorCode(error) {
  return error?.response?.data?.error?.code ?? error?.response?.data?.code ?? null;
}
export function explainError(error) {
  const code = errorCode(error);
  if (code && Object.hasOwn(messages, code)) return messages[code];
  if (["AUTH_STATE_CHANGED","AUTH_GENERATION_CHANGED","AUTH_SESSION_CHANGED"].includes(error?.message))
    return "인증 상태가 변경되었습니다. 다시 시도해 주세요.";
  if (!error?.response) return "서버 연결 또는 요청 상태를 확인해 주세요.";
  return error.response.data?.error?.message || "요청을 처리할 수 없습니다.";
}
