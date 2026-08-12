import { axiosInstance } from "@/api/mutator/custom-instance";

/**
 * 회원 탈퇴 (DELETE /members/me).
 * 비밀번호 확인 후 탈퇴 처리하며 성공 시 204 No Content.
 * 백엔드 openapi.yaml 에 아직 반영되지 않아 orval 생성 대상이 아니므로,
 * consignments.ts 등과 동일하게 axiosInstance 를 직접 사용하는 커스텀 함수로 둔다.
 */
export async function withdrawMember(password: string): Promise<void> {
  await axiosInstance.delete("/members/me", { data: { password } });
}
