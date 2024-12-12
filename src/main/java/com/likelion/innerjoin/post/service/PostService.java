package com.likelion.innerjoin.post.service;

import com.likelion.innerjoin.post.exception.*;
import com.likelion.innerjoin.post.model.dto.PostResponseDTO;
import com.likelion.innerjoin.post.model.dto.request.PostCreateRequestDTO;
import com.likelion.innerjoin.post.model.dto.request.RecruitingRequestDTO;
import com.likelion.innerjoin.post.model.dto.response.PostCreateResponseDTO;
import com.likelion.innerjoin.post.model.entity.*;
import com.likelion.innerjoin.post.repository.FormRepository;
import com.likelion.innerjoin.post.repository.PostImageRepository;
import com.likelion.innerjoin.post.repository.PostRepository;
import com.likelion.innerjoin.post.repository.RecruitingRepository;
import com.likelion.innerjoin.user.model.entity.Club;
import com.likelion.innerjoin.user.model.entity.User;
import com.likelion.innerjoin.user.repository.ClubRepository;
import com.likelion.innerjoin.user.util.SessionVerifier;
import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final PostImageRepository postImageRepository;
    private final ClubRepository clubRepository;
    private final FormRepository formRepository;
    private final RecruitingRepository recruitingRepository;
    private final SessionVerifier sessionVerifier;

    // 모든 홍보글 조회
    public List<PostResponseDTO> getAllPosts() {
        List<Post> posts = postRepository.findAll();

        if (posts.isEmpty()) {
            throw new PostNotFoundException();
        }

        return posts.stream()
                .map(this::toPostResponseDTO)
                .collect(Collectors.toList());
    }

    // 특정 홍보글 조회
    public PostResponseDTO getPostById(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException("Post not found with id: " + postId));  // post가 없으면 예외 던짐

        return toPostResponseDTO(post);
    }

    // Post 엔티티를 PostResponseDTO로 변환
    private PostResponseDTO toPostResponseDTO(Post post) {
        // PostImage 리스트를 PostImageDTO로 변환
        List<PostResponseDTO.PostImageDTO> imageDTOs = post.getImageList().stream()
                .map(image -> new PostResponseDTO.PostImageDTO(image.getId(), image.getImageUrl()))
                .collect(Collectors.toList());

        return PostResponseDTO.builder()
                .postId(post.getId())
                .clubId(post.getClub().getId())
                .title(post.getTitle())
                .content(post.getContent())
                .createdAt(post.getCreatedAt())
                .startTime(post.getStartTime())
                .endTime(post.getEndTime())
                .recruitmentStatus(post.getRecruitmentStatus().toString())
                .recruitmentCount(post.getRecruitmentCount())
                .image(imageDTOs)
                .build();
    }


    // 홍보글 작성
    @Transactional
    public PostCreateResponseDTO createPost(PostCreateRequestDTO postCreateRequestDTO, List<MultipartFile> images, HttpSession session) {

//        User user = sessionVerifier.verifySession(session);
//        if(!(user instanceof Club)){
//            throw new UnauthorizedException("권한이 없습니다.");
//        }

        Long userId = (Long) session.getAttribute("userId"); // 세션에서 Long으로 가져오기
        Club club = clubRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("잘못된 유저입니다."));

        if (!club.getId().equals(postCreateRequestDTO.getClubId())) {
            throw new UnauthorizedException("해당 Club의 권한이 아닙니다.");
        }

        // Post 엔티티 생성 및 저장
        Post post = Post.builder()
                .club(club)
                .title(postCreateRequestDTO.getTitle())
                .startTime(LocalDateTime.parse(postCreateRequestDTO.getStartTime()))
                .endTime(LocalDateTime.parse(postCreateRequestDTO.getEndTime()))
                .content(postCreateRequestDTO.getContent())
                .recruitmentStatus(RecruitmentStatus.valueOf(postCreateRequestDTO.getRecruitmentStatus()))
                .recruitmentCount(postCreateRequestDTO.getRecruitmentCount())
                .build();

        postRepository.save(post);

        // Recruiting 엔티티 생성 및 저장
        if (postCreateRequestDTO.getRecruiting() != null) {
            for (RecruitingRequestDTO recruitingRequest : postCreateRequestDTO.getRecruiting()) {
                Form form = formRepository.findById(recruitingRequest.getFormId())
                        .orElseThrow(() -> new FormNotFoundException("Form not found with id: " + recruitingRequest.getFormId()));

                // Form의 club_id가 조회한 club_id와 일치하는지 확인
                if (!form.getClub().getId().equals(club.getId())) {
                    throw new UnauthorizedException("지원폼의 club_id가 현재 유저의 club_id와 일치하지 않습니다.");
                }

                Recruiting recruiting = Recruiting.builder()
                        .form(form)
                        .post(post)
                        .jobTitle(recruitingRequest.getJobTitle())
                        .recruitmentType(RecruitmentType.valueOf(recruitingRequest.getRecruitmentType()))
                        .build();

                recruitingRepository.save(recruiting);
            }
        }

        // 이미지 처리 및 저장
        if (images != null && !images.isEmpty()) {
            for (MultipartFile image : images) {
                try {
                    String imageUrl = saveImage(image);
                    PostImage postImage = PostImage.builder()
                            .post(post)
                            .imageUrl(imageUrl)
                            .build();
                    postImageRepository.save(postImage);
                } catch (IOException e) {
                    throw new ImageProcessingException("Error processing image: " + e.getMessage(), e);
                }
            }
        }

        return new PostCreateResponseDTO(post.getId());
    }

    // 이미지 저장 메서드
    private String saveImage(MultipartFile image) throws IOException {
        // 실제 이미지 저장 로직은 Storage 사용하도록 수정 예정
        String imageUrl = "http://example.com/images/" + image.getOriginalFilename();
        return imageUrl;
    }
}
