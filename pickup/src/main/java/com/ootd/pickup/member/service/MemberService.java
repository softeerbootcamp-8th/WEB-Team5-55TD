package com.ootd.pickup.member.service;

import static com.ootd.pickup.global.exception.ExceptionCode.MEMBER_LOGIN_ID_ALREADY_EXISTS;
import static com.ootd.pickup.global.exception.ExceptionCode.MEMBER_NICKNAME_ALREADY_EXISTS;
import static com.ootd.pickup.global.exception.ExceptionCode.MEMBER_NOT_FOUND;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.ootd.pickup.global.exception.PickUpException;
import com.ootd.pickup.member.domain.Member;
import com.ootd.pickup.member.dto.MemberRequest;
import com.ootd.pickup.member.dto.MemberResponse;
import com.ootd.pickup.member.dto.MyProfileResponse;
import com.ootd.pickup.member.dto.PointBalanceResponse;
import com.ootd.pickup.member.repository.MemberRepository;
import com.ootd.pickup.point.domain.Point;
import com.ootd.pickup.point.repository.PointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class MemberService {

  private static final int BCRYPT_COST_FACTOR = 12;
  private final MemberRepository memberRepository;
  private final MemberManageService memberManageService;
  private final PointRepository pointRepository;

  public MemberResponse createMember(MemberRequest memberRequest) {
    if (memberRepository.existsByLoginId(memberRequest.loginId())) {
      throw new PickUpException(MEMBER_LOGIN_ID_ALREADY_EXISTS);
    }

    if (memberRepository.existsByNickname(memberRequest.nickname())) {
      throw new PickUpException(MEMBER_NICKNAME_ALREADY_EXISTS);
    }

    String passwordHash = hashPassword(memberRequest.password());
    Member member = Member.create(memberRequest.loginId(), passwordHash, memberRequest.nickname());

    Member savedMember;
    try {
      savedMember = memberRepository.save(member);
    } catch (DataIntegrityViolationException exception) {
      throw new PickUpException(MEMBER_LOGIN_ID_ALREADY_EXISTS);
    }

    pointRepository.save(Point.create(savedMember.getMemberId()));
    return new MemberResponse(
        savedMember.getMemberId(),
        savedMember.getLoginId(),
        savedMember.getNickname(),
        savedMember.getProfileImageUrl());
  }

  @Transactional(readOnly = true)
  public MyProfileResponse getMyProfile(Long memberId) {
    return MyProfileResponse.from(memberManageService.getMemberById(memberId));
  }

  @Transactional(readOnly = true)
  public PointBalanceResponse getMyPointBalance(Long memberId) {
    Point point =
        pointRepository
            .findByMemberId(memberId)
            .orElseThrow(() -> new PickUpException(MEMBER_NOT_FOUND));
    return new PointBalanceResponse(point.getBalance());
  }

  private String hashPassword(String rawPassword) {
    return BCrypt.withDefaults().hashToString(BCRYPT_COST_FACTOR, rawPassword.toCharArray());
  }
}
