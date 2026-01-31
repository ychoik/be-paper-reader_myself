package swyp.paperdot.doc_units.docUnits;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import swyp.paperdot.document.service.DocumentProcessingService;

@RestController
@RequestMapping("/api/test/doc-units") // 테스트용 임시 URL 경로
@RequiredArgsConstructor
public class docUnitsController {

    private final DocumentProcessingService documentProcessingService;

    /**
     * [주의: 테스트용 임시 엔드포인트]
     * 이 API는 전체 문서 처리 흐름을 테스트하기 위해 만들어졌습니다.
     * 프로덕션 환경에서는 사용하면 안 됩니다.
     */
    @Operation(summary = "[임시] PDF 텍스트 추출 및 DocUnits 저장 테스트", description = "documentId에 해당하는 PDF를 처리하여 DocUnit으로 저장합니다.")
    @PostMapping("/process/{documentId}")
    public ResponseEntity<String> processDocumentAndCreateUnits(@PathVariable Long documentId) {
        try {
            documentProcessingService.processDocumentAndCreateUnits_forTest(documentId);
            return ResponseEntity.ok("성공적으로 DocUnits 생성을 요청했습니다. documentId: " + documentId);
        } catch (Exception e) {
            // 실제로는 @RestControllerAdvice 등으로 예외를 공통 처리하는 것이 좋습니다.
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }
}