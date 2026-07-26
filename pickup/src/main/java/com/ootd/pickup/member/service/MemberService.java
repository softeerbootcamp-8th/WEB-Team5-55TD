package com.ootd.pickup.member.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.ootd.pickup.global.exception.ExceptionCode;
import com.ootd.pickup.global.exception.PickUpException;
import com.ootd.pickup.member.domain.Member;
import com.ootd.pickup.member.dto.MemberRequest;
import com.ootd.pickup.member.dto.MemberResponse;
import com.ootd.pickup.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class MemberService {

    private final MemberRepository memberRepository;
    private static final int BCRYPT_COST_FACTOR = 12;

    public MemberResponse createMember(MemberRequest memberRequest) {
        if (memberRepository.existsByLoginId(memberRequest.loginId())) {
            throw new PickUpException(ExceptionCode.MEMBER_LOGIN_ID_ALREADY_EXISTS);
        }

        if (memberRepository.existsByNickname(memberRequest.nickname())) {
            throw new PickUpException(ExceptionCode.MEMBER_NICKNAME_ALREADY_EXISTS);
        }

        String passwordHash = BCrypt.withDefaults()
                .hashToString(BCRYPT_COST_FACTOR, memberRequest.password().toCharArray());

        Member member = Member.create(
                memberRequest.loginId(),
                passwordHash,
                memberRequest.nickname()
        );

        try {
            Member savedMember = memberRepository.save(member);

            return new MemberResponse(
                    savedMember.getMemberId(),
                    savedMember.getLoginId(),
                    savedMember.getNickname(),
                    savedMember.getProfileImageUrl()
            );
        } catch (DataIntegrityViolationException exception) {
            throw new PickUpException(ExceptionCode.MEMBER_LOGIN_ID_ALREADY_EXISTS);
        }
    }

}
