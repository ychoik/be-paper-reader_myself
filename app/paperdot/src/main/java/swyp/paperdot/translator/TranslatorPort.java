package swyp.paperdot.translator;

import java.util.List;

public interface TranslatorPort {

    /**
     * 주어진 문장 리스트를 번역합니다. (내부적으로 기본 청크 사이즈 사용)
     * @param sourceSentences 번역할 원본 문장 리스트
     * @param sourceLang 원본 언어 코드 (e.g., "English")
     * @param targetLang 목표 언어 코드 (e.g., "Korean")
     * @return 번역된 문장 리스트
     */
    List<String> translateSentences(List<String> sourceSentences, String sourceLang, String targetLang);

    /**
     * 주어진 문장 리스트를 청크 단위로 나누어 번역합니다.
     * @param sourceSentences 번역할 원본 문장 리스트
     * @param sourceLang 원본 언어 코드
     * @param targetLang 목표 언어 코드
     * @param chunkSize 한 번에 API로 보낼 문장의 수
     * @return 번역된 문장 리스트
     */
    List<String> translateSentencesChunked(List<String> sourceSentences, String sourceLang, String targetLang, int chunkSize);
}
