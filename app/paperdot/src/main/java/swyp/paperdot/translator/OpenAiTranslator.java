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
import swyp.paperdot.translator.exception.TranslationException;
import swyp.paperdot.translator.exception.TranslationSizeMismatchException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class OpenAiTranslator implements TranslatorPort {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private final String apiKey;
    private final String model;
    private final String apiUrl = "https://api.openai.com/v1/chat/completions";

    private static final int DEFAULT_CHUNK_SIZE = 30; // 기본 청크 사이즈

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

    @Override
    public List<String> translateSentences(List<String> sourceSentences, String sourceLang, String targetLang) {
        return translateSentencesChunked(sourceSentences, sourceLang, targetLang, DEFAULT_CHUNK_SIZE);
    }

    @Override
    public List<String> translateSentencesChunked(List<String> sourceSentences, String sourceLang, String targetLang, int chunkSize) {
        if (CollectionUtils.isEmpty(sourceSentences)) {
            return Collections.emptyList();
        }

        // 대량의 문장을 안정적으로 처리하기 위해 chunkSize 단위로 나누어 API를 호출합니다.
        // 이는 API 요청 시간 초과, 과도한 토큰 사용, 한번에 너무 긴 텍스트로 인한 응답 품질 저하를 방지합니다.
        List<String> translatedSentences = new ArrayList<>();
        List<List<String>> chunks = partition(sourceSentences, chunkSize);

        for (List<String> chunk : chunks) {
            translatedSentences.addAll(translateSingleChunk(chunk, sourceLang, targetLang));
        }

        // 최종적으로 원본 문장 수와 번역된 문장 수가 같은지 한번 더 검증합니다.
        if (sourceSentences.size() != translatedSentences.size()) {
            throw new TranslationSizeMismatchException(
                    String.format("번역 후 문장 개수가 일치하지 않습니다. (원본: %d, 번역: %d)",
                            sourceSentences.size(), translatedSentences.size())
            );
        }
        return translatedSentences;
    }

    /**
     * 한 개의 청크(문장 묶음)를 번역하기 위해 OpenAI API를 호출합니다.
     */
    private List<String> translateSingleChunk(List<String> chunk, String sourceLang, String targetLang) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        String requestJson;
        try {
            // OpenAI에 전달할 요청 JSON을 만듭니다.
            requestJson = objectMapper.writeValueAsString(chunk);
        } catch (JsonProcessingException e) {
            // 이 예외는 발생 가능성이 매우 낮습니다.
            throw new TranslationException("요청 JSON 생성에 실패했습니다.", e);
        }

        // 시스템 메시지와 사용자 메시지를 조합하여 프롬프트를 구성합니다.
        OpenAiTranslationDto.Message systemMessage = new OpenAiTranslationDto.Message("system", createSystemPrompt(targetLang));
        OpenAiTranslationDto.Message userMessage = new OpenAiTranslationDto.Message("user", requestJson);

        // API 요청 객체 생성
        OpenAiTranslationDto.ChatRequest request = OpenAiTranslationDto.ChatRequest.of(model, List.of(systemMessage, userMessage));

        try {
            // RestTemplate으로 API 호출
            HttpEntity<OpenAiTranslationDto.ChatRequest> entity = new HttpEntity<>(request, headers);
            OpenAiTranslationDto.ChatResponse response = restTemplate.postForObject(apiUrl, entity, OpenAiTranslationDto.ChatResponse.class);

            if (response == null || CollectionUtils.isEmpty(response.getChoices())) {
                throw new TranslationException("OpenAI로부터 비어있는 응답을 받았습니다.", null);
            }

            // 응답에서 순수 JSON 배열만 추출하여 파싱합니다.
            String rawContent = response.getChoices().get(0).getMessage().content();
            List<String> translatedChunk = parseJsonArray(rawContent);

            // 응답으로 온 번역 문장의 개수가 요청한 개수와 일치하는지 검증합니다.
            if (chunk.size() != translatedChunk.size()) {
                throw new TranslationSizeMismatchException(
                        String.format("번역된 문장 개수가 일치하지 않습니다. (요청: %d, 응답: %d)", chunk.size(), translatedChunk.size())
                );
            }
            return translatedChunk;

        } catch (RestClientException e) {
            // 401(인증), 429(요청량 초과), 5xx(서버) 등 모든 HTTP 관련 예외를 포괄합니다.
            throw new TranslationException("OpenAI API 호출에 실패했습니다. " + e.getMessage(), e);
        }
    }

    /**
     * OpenAI가 안정적으로 JSON 배열만 응답하도록 유도하는 시스템 프롬프트를 생성합니다.
     */
    private String createSystemPrompt(String targetLang) {
        return String.format(
            "You are a translator that translates a JSON array of strings into %s. " +
            "You will receive a JSON array where each string is a sentence. " +
            "Your task is to translate every sentence into %s. " +
            "The response MUST be a JSON array of strings, with the exact same number of sentences as the input. " +
            "Do NOT include any additional text, explanations, or markdown formatting like ```json. " +
            "Just return the JSON array of translated sentences.",
            targetLang, targetLang
        );
    }

    /**
    * List를 주어진 사이즈의 작은 List들로 나눕니다.
    */
    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitioned = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitioned.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitioned;
    }

    /**
     * 모델의 응답에서 JSON 배열 부분만 정확히 추출합니다.
     */
    private List<String> parseJsonArray(String content) {
        // 모델이 응답 앞뒤에 불필요한 텍스트를 붙이는 경우를 대비해, '[' 와 ']' 사이의 내용만 추출합니다.
        Pattern pattern = Pattern.compile("\\[.*\\]", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            String jsonArrayString = matcher.group();
            try {
                return objectMapper.readValue(jsonArrayString, new TypeReference<>() {});
            } catch (JsonProcessingException e) {
                throw new TranslationException("번역 응답의 JSON 파싱에 실패했습니다. 응답 내용: " + content, e);
            }
        }
        throw new TranslationException("번역 응답에서 유효한 JSON 배열을 찾을 수 없습니다. 응답 내용: " + content, null);
    }
}
