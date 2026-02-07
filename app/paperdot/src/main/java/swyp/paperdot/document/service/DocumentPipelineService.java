package swyp.paperdot.document.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import swyp.paperdot.doc_units.docUnits.docUnitsEntity;
import swyp.paperdot.doc_units.docUnits.docUnitsRepository;
import swyp.paperdot.doc_units.docUnits.docUnitsService;
import swyp.paperdot.doc_units.enums.UnitStatus;
import swyp.paperdot.doc_units.enums.UnitType;
import swyp.paperdot.doc_units.translation.DocUnitTranslation;
import swyp.paperdot.doc_units.translation.DocUnitTranslationRepository;
import swyp.paperdot.translator.OpenAiTranslator;
import swyp.paperdot.translator.dto.OpenAiTranslationDto.TranslationPair; // TranslationPair import


import java.util.*;
import java.util.stream.Collectors;

/**
 * 문서 처리의 전체 흐름(파이프라인)을 오케스트레이션하는 서비스입니다.
 * 텍스트 추출, 문장 단위 분리, 저장, 번역, 번역 결과 저장 및 상태 업데이트까지의 과정을 통합 관리합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentPipelineService {

    // 의존성 주입
    private final PdfTextExtractService pdfTextExtractService;
    private final docUnitsService docUnitsService;
    private final docUnitsRepository docUnitsRepository;
    private final OpenAiTranslator openAiTranslator;
    private final DocUnitTranslationRepository docUnitTranslationRepository;

    private static final String DEFAULT_SOURCE_LANG = "en";
    private static final String DEFAULT_TARGET_LANG = "ko";

    /**
     * 특정 문서에 대한 전체 처리 파이프라인을 실행합니다.
     *
     * @param documentId 처리할 문서의 ID
     * @param overwrite  이미 처리된 'doc_units'가 있을 경우 덮어쓸지 여부
     */
    public void processDocument(Long documentId, boolean overwrite) {
        log.info("===== Document Pipeline START for documentId: {} (Overwrite: {}) =====", documentId, overwrite);

        try {
            // --- 1단계: 문서 원본 PDF에서 텍스트 추출 ---
            log.info("[Step 1/3] documentId {} - 텍스트 추출 시작", documentId);
            String rawText = pdfTextExtractService.extractText(documentId);
            log.info("[Step 1/3] documentId {} - 텍스트 추출 완료. 추출된 텍스트 길이: {}", documentId, rawText.length());

            // --- 2단계: OpenAI에 텍스트 분리 및 번역 요청 ---
            // OpenAI가 rawText를 논리적 문장으로 분리하고 각 문장을 번역하여 원본-번역 쌍을 반환합니다.
            log.info("[Step 2/3] documentId {} - OpenAI 텍스트 분리 및 번역 API 호출 시작", documentId);
            List<TranslationPair> translationPairs = openAiTranslator.extractAndTranslate(rawText, DEFAULT_TARGET_LANG);
            log.info("[Step 2/3] documentId {} - OpenAI 텍스트 분리 및 번역 API 호출 완료. {}개 문장 쌍 생성", documentId, translationPairs.size());

            // --- 3단계: 분리된 원본 텍스트(doc_units) 및 번역 결과(doc_unit_translations) DB에 저장 ---
            // 이 작업은 아래의 @Transactional 메서드에서 원자적으로 처리됩니다.
            log.info("[Step 3/3] documentId {} - 원본 텍스트 및 번역 결과 DB 저장 시작. Overwrite: {}", documentId, overwrite);
            saveTranslationsAndDocUnits(documentId, translationPairs, DEFAULT_TARGET_LANG, overwrite);
            log.info("[Step 3/3] documentId {} - 원본 텍스트 및 번역 결과 DB 저장 완료", documentId);


        } catch (Exception e) {
            log.error("===== Document Pipeline FAILED for documentId: {} =====", documentId, e); // 스택 트레이스 포함
            // 필요에 따라 특정 예외를 잡아서 다시 던지거나, 사용자 정의 예외로 변환할 수 있습니다.
            // ex) throw new DocumentProcessingServiceException("Failed to process document", e);
        } finally {
            log.info("===== Document Pipeline END for documentId: {} =====", documentId);
        }
    }

    /**
     * OpenAI에서 분리 및 번역된 원본 문장-번역 쌍을 받아 DB에 저장하고, DocUnit의 상태를 업데이트합니다.
     * 이 메서드는 하나의 트랜잭션으로 묶여 원자성을 보장합니다.
     *
     * @param documentId       처리할 문서의 ID
     * @param translationPairs 원본 문장-번역 쌍 (TranslationPair) 리스트
     * @param targetLang       목표 언어
     * @param overwrite        기존 데이터 덮어쓰기 여부
     */
        @Transactional
        public void saveTranslationsAndDocUnits(Long documentId, List<TranslationPair> translationPairs, String targetLang, boolean overwrite) {
            log.info("saveTranslationsAndDocUnits 시작: documentId {}, {}개의 번역 쌍 처리 예정", documentId, translationPairs.size());
    
            if (translationPairs.isEmpty()) {
                log.warn("documentId {} - 저장할 번역 쌍이 없습니다. 저장 작업을 건너뜝니다.", documentId);
                return;
            }
    
            // 기존 doc_units 및 doc_unit_translations 데이터 삭제 (overwrite가 true일 경우)
            if (overwrite) {
                log.info("documentId {} - Overwrite 옵션 활성화: 기존 doc_units 및 doc_unit_translations 데이터 삭제 시작.", documentId);
                docUnitTranslationRepository.deleteByDocUnitDocumentId(documentId);
                docUnitsRepository.deleteByDocumentId(documentId);
                log.info("documentId {} - 기존 doc_units 및 doc_unit_translations 데이터 삭제 완료.", documentId);
            } else {
                log.info("documentId {} - Overwrite 옵션 비활성화: 기존 데이터는 보존됩니다.", documentId);
            }
    
            List<docUnitsEntity> newDocUnits = new ArrayList<>();
                    List<DocUnitTranslation> newTranslations = new ArrayList<>();
                    int orderInDoc = 0;
                    int totalPairs = translationPairs.size();
                    int progressLogInterval = Math.max(1, totalPairs / 10); // 최소 10% 단위로 로그 출력
            
                    for (int i = 0; i < totalPairs; i++) {
                        TranslationPair pair = translationPairs.get(i);
            
                        // 1. doc_units 테이블에 원본 문장 저장 (미리 생성된 docUnit 사용)
                        docUnitsEntity docUnit = docUnitsEntity.builder()
                                .documentId(documentId)
                                .sourceText(pair.source())
                                .status(UnitStatus.TRANSLATED) // 이미 번역된 상태로 저장
                                .unitType(UnitType.SENTENCE) // 문장 단위
                                .orderInDoc(orderInDoc++) // 순서 지정
                                .build();
                        newDocUnits.add(docUnit);
            
                        // 진행률 로그 (매 10% 단위 또는 최소 10개마다)
                        if (totalPairs > 10 && (i + 1) % progressLogInterval == 0 || (i + 1) == totalPairs) {
                            log.info("documentId {} - DocUnit 및 번역 저장 진행률: {}/{} ({:.0f}%)",
                                    documentId, (i + 1), totalPairs, ((double)(i + 1) / totalPairs * 100));
                        }
                    }
                    docUnitsRepository.saveAll(newDocUnits); // doc_units 일괄 저장
                    log.info("documentId {} - {}개의 DocUnit(원본 문장)을 DB에 저장했습니다.", documentId, newDocUnits.size());
            
                    // 2. doc_unit_translations 테이블에 번역 결과 저장
                    for (docUnitsEntity docUnit : newDocUnits) {
                        // 해당 docUnit에 매칭되는 TranslationPair를 찾아야 하지만,
                        // 현재 로직상 newDocUnits와 translationPairs는 1:1 순서로 매칭됩니다.
                        // 따라서 인덱스를 사용하여 매칭합니다. (OpenAI가 순서를 보장한다고 가정)
                        TranslationPair matchingPair = translationPairs.get(docUnit.getOrderInDoc());
            
                        DocUnitTranslation translation = DocUnitTranslation.builder()
                                .docUnit(docUnit)
                                .targetLang(targetLang)
                                .translatedText(matchingPair.translated())
                                .build();
                        newTranslations.add(translation);
                    }            docUnitTranslationRepository.saveAll(newTranslations); // doc_unit_translations 일괄 저장
            log.info("documentId {} - {}개의 번역 결과를 DB에 저장했습니다.", documentId, newTranslations.size());
            log.info("saveTranslationsAndDocUnits 완료: documentId {}", documentId);
        }

    /**
     * 비동기적으로 문서 처리 파이프라인을 실행합니다.
     * 컨트롤러는 이 메서드를 호출하여 즉시 응답을 반환하고, 실제 처리는 백그라운드 스레드에서 수행됩니다.
     * @param documentId 처리할 문서의 ID
     * @param overwrite  이미 처리된 'doc_units'가 있을 경우 덮어쓸지 여부
     */
    @org.springframework.scheduling.annotation.Async
    public void processDocumentAsync(Long documentId, boolean overwrite) {
        log.info("[Async Start] documentId {} 문서 파이프라인 비동기 처리 시작. Overwrite: {}", documentId, overwrite);
        processDocument(documentId, overwrite);
        log.info("[Async End] documentId {} 문서 파이프라인 비동기 처리 완료.", documentId);
    }

    /**
     * 특정 문서에 대한 원문(DocUnit)과 번역문(DocUnitTranslation) 쌍을 1:1로 매칭하여 반환합니다.
     *
     * @param documentId 조회할 문서의 ID
     * @return 원문-번역 쌍 DTO 리스트
     */
    @Transactional(readOnly = true)
    public List<swyp.paperdot.document.dto.DocumentTranslationPairResponse> getTranslationPairsForDocument(Long documentId) {
        // 1. documentId에 해당하는 모든 docUnitsEntity를 orderInDoc 순서대로 조회
        List<docUnitsEntity> docUnits = docUnitsRepository.findByDocumentIdOrderByOrderInDocAsc(documentId);

        if (CollectionUtils.isEmpty(docUnits)) {
            log.warn("documentId {} 에 해당하는 DocUnit이 없습니다.", documentId);
            return Collections.emptyList();
        }

        // 2. DocUnitTranslation에서 해당 documentId의 번역들을 조회하여 Map으로 구성
        // (JPA의 ManyToOne 관계를 통해 DocUnit을 기준으로 번역을 가져올 수 있으나,
        // 벌크로 가져와서 매핑하는 것이 N+1 쿼리 문제를 피할 수 있어 효율적일 수 있습니다.)
        List<DocUnitTranslation> translations = docUnitTranslationRepository.findByDocUnitDocumentId(documentId);
        // doc_units_translation 테이블에는 하나의 doc_unit_id에 대해 하나의 번역만 있다고 가정
        java.util.Map<Long, String> translatedTextMap = translations.stream()
                .collect(Collectors.toMap(
                        dt -> dt.getDocUnit().getId(), // DocUnit ID를 키로 사용
                        DocUnitTranslation::getTranslatedText
                ));

        // 3. docUnits와 translatedTextMap을 조합하여 DTO 리스트 생성
        return docUnits.stream()
                .map(docUnit -> swyp.paperdot.document.dto.DocumentTranslationPairResponse.builder()
                        .docUnitId(docUnit.getId())
                        .sourceText(docUnit.getSourceText())
                        .translatedText(translatedTextMap.getOrDefault(docUnit.getId(), "")) // 번역이 없는 경우 빈 문자열 반환
                        .build())
                .collect(Collectors.toList());
    }
}
