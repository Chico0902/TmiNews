package tmi.app.controller;

import tmi.app.dto.NewsPreviewRequest;
import tmi.app.dto.NewsPreviewResponse;
import tmi.app.dto.NewsRegisterRequest;
import tmi.app.service.NewsService;
import tmi.app.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.springframework.web.client.RestTemplate;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/news")
public class NewsController {

  @Value("${ai.service.url}")
  private String aiServiceUrl;

  private final NewsService newsService;
  private final JwtProvider jwtProvider;
  private static final Logger logger = LoggerFactory.getLogger(NewsController.class);

  private final RestTemplate restTemplate;

  @PostMapping("/preview")
  public ResponseEntity<NewsPreviewResponse> previewNews(
      @RequestHeader("Authorization") String bearerToken,
      @RequestBody NewsPreviewRequest request) {

    // JWT 검증
    String token = bearerToken.replace("Bearer ", "");
    logger.info("JWT 토큰 추출: {}", token);
    Long userId = jwtProvider.extractUserId(token);
    logger.info("토큰으로 추출한 userId: {}", userId);

    // category 유효성 체크
    String[] categories = { "IT", "연예", "스포츠", "사회", "건강", "재테크" };
    boolean validCategory = false;
    for (String c : categories) {
      if (c.equals(request.getCategory())) {
        validCategory = true;
        break;
      }
    }
    if (!validCategory) {
      logger.warn("유효하지 않은 카테고리: {}", request.getCategory());
      return ResponseEntity.badRequest().build();
    }

    // news_time 대체
    String newsTime = request.getNewsTime();
    if (newsTime == null || newsTime.isEmpty()) {
      newsTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
    logger.info("뉴스 시간: {}", newsTime);

    // AI 서버 호출 - RestTemplate 사용 예시
    String url = aiServiceUrl + "/generate";
    logger.info("Directly calling AI endpoint at: {}", url);

    Map<String, String> requestBody = Map.of(
        "title", request.getTitle(),
        "content", request.getContent(),
        "category", request.getCategory());

    ResponseEntity<Map> aiResponseEntity = restTemplate.postForEntity(url, requestBody, Map.class);
    Map<String, String> aiResponse = aiResponseEntity.getBody();
    logger.info("AI 서버 응답: {}", aiResponse);

    // 응답 해석 및 반환
    NewsPreviewResponse response = new NewsPreviewResponse();
    response.setTitle(aiResponse.get("generated_title"));
    response.setContent(aiResponse.get("generated_content"));
    response.setCategory(request.getCategory());
    response.setNewsTime(newsTime);
    logger.info("응답 반환 완료");
    return ResponseEntity.ok(response);
  }

  @PostMapping
  public ResponseEntity<?> registerNews(@RequestBody NewsRegisterRequest request) {
    newsService.registerNews(request);
    return ResponseEntity.status(201).build();
  }
}
