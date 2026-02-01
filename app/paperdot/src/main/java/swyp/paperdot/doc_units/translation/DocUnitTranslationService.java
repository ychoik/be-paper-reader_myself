package swyp.paperdot.doc_units.translation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import swyp.paperdot.doc_units.docUnits.docUnitsEntity;
import swyp.paperdot.doc_units.docUnits.docUnitsRepository;
import swyp.paperdot.doc_units.enums.UnitStatus;
import swyp.paperdot.translator.TranslatorPort;
import swyp.paperdot.translator.exception.TranslationException;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocUnitTranslationService {

    private final docUnitsRepository docUnitsRepository;
    private final DocUnitTranslationRepository docUnitTranslationRepository;
    private final TranslatorPort translatorPort;

    /**
     * 특정 문서(document)에 포함된 모든 CREATED 상태의 DocUnit들을 번역하고 저장합니다.
     * 이 메서드는 전체 과정이 하나의 트랜잭션으로 처리됩니다.
     *
     * @param documentId 번역할 문서의 ID
     * @param sourceLang 원본 언어 (e.g., "English")
     * @param targetLang 목표 언어 (e.g., "Korean")
     */
    @Transactional
    public void translateDocUnits(Long documentId, String sourceLang, String targetLang) {
        // --- 1. DocUnit 조회 ---
        // documentId와 CREATED 상태를 기준으로 번역 대상 유닛들을 order_in_doc 순서대로 조회합니다.
        // 이 단계에서 대상 유닛이 없으면 아무 작업도 수행하지 않고 종료합니다.
        List<docUnitsEntity> unitsToTranslate = docUnitsRepository.findByDocumentIdAndStatusOrderByOrderInDocAsc(documentId, UnitStatus.CREATED);
        if (CollectionUtils.isEmpty(unitsToTranslate)) {
            log.info("번역할 DocUnit(상태: CREATED)이 없습니다. documentId: {}", documentId);
            return;
        }

        // --- 2. 원본 텍스트 추출 ---
        // 조회된 유닛들에서 번역할 원본 텍스트만 순서대로 추출합니다.
        List<String> sourceTexts = unitsToTranslate.stream()
                .map(docUnitsEntity::getSourceText)
                .collect(Collectors.toList());

        // --- 3. 외부 번역 API 호출 ---
        // TranslatorPort를 통해 외부 OpenAI API를 호출하여 번역을 수행합니다.
        // 이 단계에서 API 호출 실패, 타임아웃, 응답 파싱 실패 등이 발생하면 TranslationException이 발생합니다.
        // @Transactional에 의해, 이 예외가 발생하면 지금까지의 모든 DB 작업(만약 있다면)이 롤백됩니다.
        // 여기서는 아직 DB 변경이 없었으므로, 실제 롤백될 내용은 없습니다.
        List<String> translatedTexts;
        try {
            // OpenAiTranslator의 기본 청크 사이즈를 사용하는 translateSentences 메서드를 호출합니다.
            translatedTexts = translatorPort.translateSentences(sourceTexts, sourceLang, targetLang);
        } catch (TranslationException e) {
            // 외부 API 호출 관련 예외는 서비스 레벨에서 처리하기 어렵기 때문에,
            // 에러 로그를 남기고 RuntimeException으로 다시 던져 트랜잭션을 롤백시킵니다.
            log.error("DocUnit 번역 중 외부 API 호출에 실패했습니다. documentId: {}", documentId, e);
            throw new RuntimeException("번역 서비스 호출에 실패하여 작업을 롤백합니다.", e);
        }

        // 원본과 번역본의 개수가 다르면 데이터 정합성에 문제가 발생하므로 트랜잭션을 롤백시킵니다.
        if (sourceTexts.size() != translatedTexts.size()) {
            throw new IllegalStateException("번역 후 문장 개수가 일치하지 않아 작업을 롤백합니다.");
        }

        // --- 4. 번역 결과 엔티티 생성 및 저장 ---
        // 번역된 텍스트와 원본 DocUnit을 매핑하여 DocUnitTranslation 엔티티 리스트를 생성합니다.
        List<DocUnitTranslation> newTranslations = new ArrayList<>();
        for (int i = 0; i < unitsToTranslate.size(); i++) {
            docUnitsEntity originalUnit = unitsToTranslate.get(i);
            String translatedText = translatedTexts.get(i);

            DocUnitTranslation translation = DocUnitTranslation.builder()
                    .docUnit(originalUnit)
                    .targetLang(targetLang)
                    .translatedText(translatedText)
                    .build();
            newTranslations.add(translation);
        }

        // 생성된 번역 엔티티들을 DB에 일괄 저장(batch insert)합니다.
        // 이 saveAll 작업 중 DB 제약조건 위반 등 예외 발생 시, 트랜잭션이 롤백됩니다.
        docUnitTranslationRepository.saveAll(newTranslations);

        // --- 5. 원본 DocUnit 상태 업데이트 ---
        // 번역이 성공적으로 완료된 DocUnit들의 상태를 TRANSLATED로 변경합니다.
        // JPA의 변경 감지(dirty checking) 기능에 의해, 트랜잭션이 커밋될 때 UPDATE 쿼리가 자동으로 실행됩니다.
        // 이 과정에서 문제가 발생해도 전체 트랜잭션이 롤백됩니다.
        for (docUnitsEntity unit : unitsToTranslate) {
            unit.updateStatus(UnitStatus.TRANSLATED);
        }

        log.info("DocUnit 번역 완료. documentId: {}, 번역된 유닛 개수: {}", documentId, unitsToTranslate.size());
    }
}
