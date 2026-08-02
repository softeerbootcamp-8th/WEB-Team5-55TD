package com.ootd.pickup.member.service;

import static com.ootd.pickup.global.exception.ExceptionCode.INVALID_PASSWORD;
import static com.ootd.pickup.global.exception.ExceptionCode.MEMBER_LOGIN_ID_ALREADY_EXISTS;
import static com.ootd.pickup.global.exception.ExceptionCode.MEMBER_NICKNAME_ALREADY_EXISTS;
import static com.ootd.pickup.global.exception.ExceptionCode.MEMBER_NOT_FOUND;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.ootd.pickup.global.exception.PickUpException;
import com.ootd.pickup.images.domain.ImagePurpose;
import com.ootd.pickup.images.service.ImageService;
import com.ootd.pickup.images.service.ImageService.FinalizedImage;
import com.ootd.pickup.images.service.ImageUrlResolver;
import com.ootd.pickup.member.domain.Member;
import com.ootd.pickup.member.dto.*;
import com.ootd.pickup.member.repository.MemberRepository;
import com.ootd.pickup.point.domain.Point;
import com.ootd.pickup.point.repository.PointRepository;
import java.util.List;
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
  private final ImageService imageService;
  private final ImageUrlResolver imageUrlResolver;

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
        savedMember.getMemberId(), savedMember.getLoginId(), savedMember.getNickname(), null);
  }

  @Transactional(readOnly = true)
  public MyProfileResponse getMyProfile(Long memberId) {
    Member member = memberManageService.getMemberById(memberId);
    return toMyProfileResponse(member);
  }

  public MyProfileResponse updateMyProfile(
      Long memberId, UpdateMyProfileRequest updateMyProfileRequest) {
    Member member = memberManageService.getMemberById(memberId);
    String nickname = updateMyProfileRequest.nickname();

    if (updateMyProfileRequest.password() != null
        && !member.isPasswordMatched(updateMyProfileRequest.currentPassword())) {
      throw new PickUpException(INVALID_PASSWORD);
    }

    if (nickname != null
        && !nickname.equals(member.getNickname())
        && memberRepository.existsByNickname(nickname)) {
      throw new PickUpException(MEMBER_NICKNAME_ALREADY_EXISTS);
    }

    String passwordHash =
        updateMyProfileRequest.password() == null
            ? null
            : hashPassword(updateMyProfileRequest.password());
    member.updateProfile(nickname, passwordHash);
    updateProfileImage(memberId, member, updateMyProfileRequest);
    return toMyProfileResponse(member);
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

  private void updateProfileImage(
      Long memberId, Member member, UpdateMyProfileRequest updateMyProfileRequest) {
    ProfileImageUpdateRequest profileImageUpdate = updateMyProfileRequest.profileImageUpdate();
    if (profileImageUpdate == null) {
      return;
    }

    String previousObjectKey = member.getProfileImageObjectKey();
    switch (profileImageUpdate.action()) {
      case SET -> {
        FinalizedImage finalizedImage =
            imageService
                .finalizeImages(
                    memberId,
                    ImagePurpose.PROFILE,
                    List.of(profileImageUpdate.temporaryObjectKey()))
                .getFirst();
        member.updateProfileImage(finalizedImage.objectKey());
      }
      case REMOVE -> member.removeProfileImage();
    }

    if (previousObjectKey != null && !previousObjectKey.equals(member.getProfileImageObjectKey())) {
      imageService.deleteAfterCommit(List.of(previousObjectKey));
    }
  }

  private MyProfileResponse toMyProfileResponse(Member member) {
    return MyProfileResponse.from(
        member, imageUrlResolver.resolve(member.getProfileImageObjectKey()));
  }
}
