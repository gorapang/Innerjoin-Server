package com.likelion.innerjoin.user.service;

import com.likelion.innerjoin.common.exception.ErrorCode;
import com.likelion.innerjoin.common.response.CommonResponse;
import com.likelion.innerjoin.user.exception.EmailValidationException;
import com.likelion.innerjoin.user.exception.UnivCertException;
import com.likelion.innerjoin.user.exception.UnivEmailDomainException;
import com.likelion.innerjoin.user.exception.UnivNameException;
import com.likelion.innerjoin.user.model.dto.request.UnivCertRequestDto;
import com.likelion.innerjoin.user.model.dto.response.UnivCertResponseDto;
import com.univcert.api.UnivCert;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class UnivCertService {

    @Value("${univcert.api-key}")
    private String apiKey;

    @Value("${univcert.univ-check:true}")
    private boolean univCheck;

    public boolean isUnivNameValid(String univName) {
        try {
            Map<String, Object> responseMap = UnivCert.check(univName);

            return Boolean.TRUE.equals(responseMap.get("success"));
        } catch (Exception e) {
            throw new UnivNameException("학교명을 확인해주세요");
        }
    }

    public void certifyEmail(UnivCertRequestDto requestDto) {
        String email = requestDto.getEmail();
        String univName = requestDto.getUnivName();

        if (email == null || !email.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            throw new EmailValidationException("이메일 형식이 잘못되었습니다.");
        }

        if (!isUnivNameValid(univName)) {
            throw new UnivNameException("유효하지 않은 학교명입니다.");
        }

        try {
            Map<String, Object> responseMap = UnivCert.certify(apiKey, email, univName, univCheck);

            if (!Boolean.TRUE.equals(responseMap.get("success"))) {
                String errorMessage = (String) responseMap.get("message");
                throw new UnivCertException(errorMessage);
            }
        } catch (Exception e) {
            throw new UnivEmailDomainException("학교명과 이메일 도메인이 일치하지 않습니다.");
        }
    }
}