package com.ootd.pickup.member.service;

import static com.ootd.pickup.global.exception.ExceptionCode.*;

import com.ootd.pickup.global.exception.PickUpException;
import com.ootd.pickup.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberManageService {

  private final MemberRepository memberRepository;

  public void validateMemberExists(Long memberId) {
    if (!memberRepository.existsById(memberId)) {
      throw new PickUpException(MEMBER_NOT_FOUND);
    }
  }
}
