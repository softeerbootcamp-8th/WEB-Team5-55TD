package com.ootd.pickup.member.api;

import com.ootd.pickup.auction.dto.request.GetMyWatchesRequest;
import com.ootd.pickup.auction.dto.response.AuctionListItemResponse;
import com.ootd.pickup.bid.dto.request.GetMyBidsRequest;
import com.ootd.pickup.bid.dto.request.GetMyWinsRequest;
import com.ootd.pickup.bid.dto.response.MyBidListItemResponse;
import com.ootd.pickup.global.config.SwaggerConfig;
import com.ootd.pickup.global.dto.response.CursorPageResponse;
import com.ootd.pickup.global.exception.dto.response.ExceptionResponse;
import com.ootd.pickup.member.dto.MemberRequest;
import com.ootd.pickup.member.dto.MemberResponse;
import com.ootd.pickup.member.dto.MyProfileResponse;
import com.ootd.pickup.member.dto.PointBalanceResponse;
import com.ootd.pickup.member.dto.UpdateMyProfileRequest;
import com.ootd.pickup.member.dto.WithdrawMemberRequest;
import com.ootd.pickup.point.dto.request.ChargePointRequest;
import com.ootd.pickup.point.dto.request.GetPointTransactionsRequest;
import com.ootd.pickup.point.dto.response.PointChargeResponse;
import com.ootd.pickup.point.dto.response.PointTransactionItemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Member", description = "회원 API")
public interface MemberApi {

  @Operation(
      summary = "회원가입",
      description = "로그인 ID, 닉네임, 비밀번호로 회원을 생성합니다.",
      responses = {
        @ApiResponse(
            responseCode = "201",
            description = "회원가입 성공",
            content = @Content(schema = @Schema(implementation = MemberResponse.class))),
        @ApiResponse(
            responseCode = "409",
            description = "로그인 ID 또는 닉네임 중복",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
      })
  ResponseEntity<MemberResponse> createMember(MemberRequest request);

  @Operation(
      summary = "내 정보 조회",
      security = @SecurityRequirement(name = SwaggerConfig.ACCESS_TOKEN_SECURITY_SCHEME),
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "내 정보 조회 성공",
            content = @Content(schema = @Schema(implementation = MyProfileResponse.class))),
        @ApiResponse(
            responseCode = "401",
            description = "인증 필요",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
        @ApiResponse(
            responseCode = "404",
            description = "회원 없음",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
      })
  ResponseEntity<MyProfileResponse> getMyProfile(@Parameter(hidden = true) Long memberId);

  @Operation(
      summary = "내 정보 수정",
      description =
          "전달된 닉네임, 비밀번호, 프로필 이미지만 수정합니다. 프로필 이미지는 SET 또는 REMOVE로 변경하며 비밀번호 변경 시 현재 비밀번호가 필요합니다.",
      security = @SecurityRequirement(name = SwaggerConfig.ACCESS_TOKEN_SECURITY_SCHEME),
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "내 정보 수정 성공",
            content = @Content(schema = @Schema(implementation = MyProfileResponse.class))),
        @ApiResponse(
            responseCode = "400",
            description = "요청 값 검증 실패",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
        @ApiResponse(
            responseCode = "401",
            description = "인증 필요",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
        @ApiResponse(
            responseCode = "404",
            description = "회원 없음",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
        @ApiResponse(
            responseCode = "409",
            description = "닉네임 중복",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
      })
  ResponseEntity<MyProfileResponse> updateMyProfile(
      @Parameter(hidden = true) Long memberId, UpdateMyProfileRequest request);

  @Operation(
      summary = "내 포인트 잔액 조회",
      security = @SecurityRequirement(name = SwaggerConfig.ACCESS_TOKEN_SECURITY_SCHEME),
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "포인트 잔액 조회 성공",
            content = @Content(schema = @Schema(implementation = PointBalanceResponse.class))),
        @ApiResponse(
            responseCode = "401",
            description = "인증 필요",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
        @ApiResponse(
            responseCode = "404",
            description = "회원 없음",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
      })
  ResponseEntity<PointBalanceResponse> getMyPointBalance(@Parameter(hidden = true) Long memberId);

  @Operation(
      summary = "내 포인트 거래내역 조회",
      description = "실제 포인트 증감 내역을 최신순 커서 기반으로 조회합니다.",
      security = @SecurityRequirement(name = SwaggerConfig.ACCESS_TOKEN_SECURITY_SCHEME),
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "포인트 거래내역 조회 성공",
            content = @Content(schema = @Schema(implementation = CursorPageResponse.class))),
        @ApiResponse(
            responseCode = "401",
            description = "인증 필요",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
      })
  ResponseEntity<CursorPageResponse<PointTransactionItemResponse, String>> getMyPointTransactions(
      @Parameter(hidden = true) Long memberId, GetPointTransactionsRequest request);

  @Operation(
      summary = "포인트 충전",
      description =
          "실제 결제 연동 없이 목업으로 포인트를 즉시 적립합니다. 같은 idempotencyKey로 재요청하면 중복 적립되지 않고 이전 결과를 그대로 반환합니다.",
      security = @SecurityRequirement(name = SwaggerConfig.ACCESS_TOKEN_SECURITY_SCHEME),
      responses = {
        @ApiResponse(
            responseCode = "201",
            description = "포인트 충전 성공",
            content = @Content(schema = @Schema(implementation = PointChargeResponse.class))),
        @ApiResponse(
            responseCode = "200",
            description = "이미 처리된 충전 요청(같은 idempotencyKey 재요청)",
            content = @Content(schema = @Schema(implementation = PointChargeResponse.class))),
        @ApiResponse(
            responseCode = "400",
            description = "유효하지 않은 충전 금액",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
        @ApiResponse(
            responseCode = "401",
            description = "인증 필요",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
        @ApiResponse(
            responseCode = "404",
            description = "회원 없음",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
      })
  ResponseEntity<PointChargeResponse> chargeMyPoint(
      @Parameter(hidden = true) Long memberId, ChargePointRequest request);

  @Operation(
      summary = "내 입찰 내역 조회",
      description = "회원이 입찰한 경매 목록을 최근 입찰 순으로 조회합니다. 경매당 마지막(최신) 입찰만 반환합니다.",
      security = @SecurityRequirement(name = SwaggerConfig.ACCESS_TOKEN_SECURITY_SCHEME),
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "내 입찰 내역 조회 성공",
            content = @Content(schema = @Schema(implementation = CursorPageResponse.class))),
        @ApiResponse(
            responseCode = "400",
            description = "유효하지 않은 커서 값",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
        @ApiResponse(
            responseCode = "401",
            description = "인증 필요",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
      })
  ResponseEntity<CursorPageResponse<MyBidListItemResponse, String>> getMyBids(
      @Parameter(hidden = true) Long memberId, GetMyBidsRequest getMyBidsRequest);

  @Operation(
      summary = "내 낙찰 내역 조회",
      description = "회원이 낙찰받은 경매 목록을 최근 입찰 순으로 조회합니다. 경매당 마지막(최신) 입찰이 낙찰(WON)인 것만 반환합니다.",
      security = @SecurityRequirement(name = SwaggerConfig.ACCESS_TOKEN_SECURITY_SCHEME),
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "내 낙찰 내역 조회 성공",
            content = @Content(schema = @Schema(implementation = CursorPageResponse.class))),
        @ApiResponse(
            responseCode = "400",
            description = "유효하지 않은 커서 값",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
        @ApiResponse(
            responseCode = "401",
            description = "인증 필요",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
      })
  ResponseEntity<CursorPageResponse<MyBidListItemResponse, String>> getMyWins(
      @Parameter(hidden = true) Long memberId, GetMyWinsRequest getMyWinsRequest);

  @Operation(
      summary = "관심 목록 조회",
      description = "관심 등록한 예정(SCHEDULED) 및 진행 중(ONGOING) 경매를 최신순으로 조회합니다. 낙찰되면 관심 목록에서 노출되지 않습니다.",
      security = @SecurityRequirement(name = SwaggerConfig.ACCESS_TOKEN_SECURITY_SCHEME),
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "관심 목록 조회 성공",
            content =
                @Content(
                    schema = @Schema(implementation = CursorPageResponse.class),
                    examples =
                        @ExampleObject(
                            value =
                                """
                            {
                              "hasNext": true,
                              "cursor": "string",
                              "size": 20,
                              "items": [
                                {
                                  "auctionId": 0,
                                  "consignmentId": 0,
                                  "card": {
                                    "cardId": 0,
                                    "cardName": "리자몽 1st Edition Holo",
                                    "setName": "Base Set",
                                    "cardNumber": "4/102",
                                    "language": "일본어",
                                    "rarity": "홀로 레어",
                                    "imageUrl": "string"
                                  },
                                  "grade": "PSA 10",
                                  "auctionStatus": "SCHEDULED",
                                  "startingPrice": 0,
                                  "currentPrice": null,
                                  "startedAt": "2026-08-03T12:00:00",
                                  "endedAt": null,
                                  "remainingSeconds": null,
                                  "watchCount": 0,
                                  "watched": true,
                                  "thumbnailUrl": "string"
                                }
                              ]
                            }
                            """))),
        @ApiResponse(
            responseCode = "400",
            description = "유효하지 않은 커서 값 또는 size 값(1 미만)",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
        @ApiResponse(
            responseCode = "401",
            description = "인증 필요",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
      })
  ResponseEntity<CursorPageResponse<AuctionListItemResponse, String>> getMyWatches(
      @Parameter(hidden = true) Long memberId, GetMyWatchesRequest getMyWatchesRequest);

  @Operation(
      summary = "회원 탈퇴",
      description =
          "비밀번호 확인 후 회원을 탈퇴 처리합니다. 로그인 아이디는 비워져 재로그인할 수 없게 되며, 셀러로 등록한 상품이 경매 예정/진행 중이거나 최고 입찰자인"
              + " 경매가 있으면 탈퇴할 수 없습니다.",
      security = @SecurityRequirement(name = SwaggerConfig.ACCESS_TOKEN_SECURITY_SCHEME),
      responses = {
        @ApiResponse(responseCode = "204", description = "탈퇴 성공"),
        @ApiResponse(
            responseCode = "400",
            description = "요청 값 검증 실패",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
        @ApiResponse(
            responseCode = "401",
            description = "인증 필요 또는 비밀번호 불일치",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
        @ApiResponse(
            responseCode = "404",
            description = "회원 없음",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
        @ApiResponse(
            responseCode = "409",
            description = "이미 탈퇴한 회원 또는 진행 중인 경매/입찰이 있어 탈퇴할 수 없음",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
      })
  ResponseEntity<Void> withdrawMember(
      @Parameter(hidden = true) Long memberId, WithdrawMemberRequest request);
}
