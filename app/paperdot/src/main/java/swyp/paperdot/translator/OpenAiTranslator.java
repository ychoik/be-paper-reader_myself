package swyp.paperdot.translator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import swyp.paperdot.translator.dto.OpenAiTranslationDto;
import swyp.paperdot.translator.dto.OpenAiTranslationDto.TranslationPair; // TranslationPair import
import swyp.paperdot.translator.exception.TranslationException;

import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class OpenAiTranslator implements TranslatorPort {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private final String apiKey;
    private final String model;
    private final String apiUrl = "https://api.openai.com/v1/chat/completions";

    public OpenAiTranslator(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Value("${openai.api.key}") String apiKey,
            @Value("${openai.api.model}") String model
    ) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
    }

    // --- 기존 TranslatorPort 인터페이스 메서드는 더 이상 사용하지 않으므로 변경 또는 제거 필요 ---
    // --- TranslatorPort 인터페이스를 먼저 수정하는 것이 좋지만, 일단 UnsupportedOperationException 처리 ---



    // --- 새로운 핵심 메서드: 원본 텍스트를 분리하고 번역까지 수행 ---
    /**
     * 원본 텍스트를 OpenAI에 보내 논리적 문장 단위로 분리하고, 각 문장을 번역하여 원본-번역 쌍의 리스트를 반환합니다.
     * @param rawText 원본 전체 텍스트
     * @param targetLang 번역 목표 언어
     * @return 원본-번역 쌍 (TranslationPair) 리스트
     */
    public List<TranslationPair> extractAndTranslate(String rawText, String targetLang) {
        if (rawText == null || rawText.isBlank()) {
            return Collections.emptyList();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // 시스템 메시지와 사용자 메시지를 조합하여 프롬프트를 구성합니다.
        OpenAiTranslationDto.Message systemMessage = new OpenAiTranslationDto.Message("system", createSystemPrompt(targetLang));
        // rawText를 직접 사용자 입력으로 전달하여 OpenAI가 분리 및 번역하도록 요청
        // TODO: rawText가 너무 길 경우 토큰 제한에 걸릴 수 있으므로, 청크로 나누어 처리하는 로직 추가 필요 (향후 개선점)
        OpenAiTranslationDto.Message userMessage = new OpenAiTranslationDto.Message("user", rawText);

        // API 요청 객체 생성
        OpenAiTranslationDto.ChatRequest request = OpenAiTranslationDto.ChatRequest.of(model, List.of(systemMessage, userMessage));

        if (log.isDebugEnabled()) {
            log.debug("OpenAI API Key (masked): {}", apiKey != null && apiKey.length() > 5 ? apiKey.substring(0, 5) + "..." : apiKey);
            log.debug("OpenAI API Request Headers: {}", headers);
            try {
                log.debug("OpenAI API Request Body: {}", objectMapper.writeValueAsString(request));
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize OpenAI request for logging", e);
            }
        }
        try {
            // RestTemplate으로 API 호출
            HttpEntity<OpenAiTranslationDto.ChatRequest> entity = new HttpEntity<>(request, headers);
            OpenAiTranslationDto.ChatResponse response = restTemplate.postForObject(apiUrl, entity, OpenAiTranslationDto.ChatResponse.class);

            if (log.isDebugEnabled()) {
                try {
                    log.debug("OpenAI API Raw Response: {}", objectMapper.writeValueAsString(response));
                } catch (JsonProcessingException e) {
                    log.error("Failed to serialize OpenAI response for logging", e);
                }
            }

            if (response == null || CollectionUtils.isEmpty(response.getChoices())) {
                throw new TranslationException("OpenAI로부터 비어있는 응답을 받았습니다.", null);
            }

            // 응답에서 JSON 배열만 추출하여 TranslationPair 리스트로 파싱합니다.
            String rawContent = response.getChoices().get(0).getMessage().content();
            List<TranslationPair> translationPairs = parseOpenAiResponseForPairs(rawContent);

            // OpenAI가 반환한 객체 개수가 프롬프트 지시를 따랐는지 검증할 수 있습니다.
            // 프롬프트는 "number of objects MUST be exactly the same as the number of logical sentences you identify"
            // 라고 지시하고 있으므로, parseOpenAiResponseForPairs에서 파싱된 개수를 신뢰합니다.
            // 필요하다면 이곳에 추가 검증 로직을 구현할 수 있습니다.

            return translationPairs;

        } catch (RestClientException e) {
            throw new TranslationException("OpenAI API 호출에 실패했습니다. " + e.getMessage(), e);
        }
    }


    /**
     * OpenAI가 원본 텍스트를 문장 단위로 분리하고 번역하도록 유도하는 시스템 프롬프트를 생성합니다.
     */
    private String createSystemPrompt(String targetLang) {
        return String.format(
            "You are a translator that takes raw text, splits it into logical sentences, and translates each sentence into %s. " +
            "The response MUST be a JSON array of objects. Each object MUST contain two keys: 'source' for an original logical sentence and 'translated' for its corresponding translated sentence. " +
            "The number of objects in the array MUST be exactly the same as the number of logical sentences you identify in the input text. " +
            "Do NOT include any additional text, explanations, or markdown formatting outside the JSON array. " +
            "Ensure that the string values for 'source' and 'translated' are valid JSON strings, properly escaping any internal double quotes or special characters. " +
            "Example: [{\"source\": \"Hello World.\", \"translated\": \"안녕하세요.\"}, {\"source\": \"How are you?\", \"translated\": \"어떠세요?\"}]",
            targetLang
        );
    }



    /**
     * 모델의 응답에서 JSON 객체를 파싱하고 TranslationPair 리스트를 추출합니다.
     */
    private List<TranslationPair> parseOpenAiResponseForPairs(String content) {
        try {
            TypeReference<List<TranslationPair>> typeRef = new TypeReference<>() {};
            List<TranslationPair> translationPairs = objectMapper.readValue(content, typeRef);

            if (translationPairs == null) {
                throw new TranslationException("번역 응답에서 TranslationPair 리스트를 찾을 수 없습니다. 응답 내용: " + content, null);
            }
            return translationPairs;

        } catch (JsonProcessingException e) {
            throw new TranslationException("번역 응답의 JSON 파싱에 실패했습니다. 응답 내용: " + content, e);
        }
    }


}