package swyp.paperdot.document.service;

import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader; // Loader 클래스 import
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import swyp.paperdot.document.exception.DocumentNotFoundException;
import swyp.paperdot.document.exception.PdfParseException;
import swyp.paperdot.document.exception.StorageDownloadException;

import java.io.IOException;
import java.io.InputStream;
// Files, Path, StandardCopyOption은 더 이상 필요 없으므로 제거 (혹시 이전 코드에 있었다면)

/**
 * PDF 파일에서 텍스트를 추출하는 비즈니스 로직을 처리하는 서비스 클래스입니다.
 */
@Service
@RequiredArgsConstructor
public class PdfTextExtractService {

    private final DocumentDownloadService documentDownloadService;

    /**
     * 주어진 문서 ID에 해당하는 원본 PDF 파일에서 텍스트 전체를 추출하여 반환합니다.
     *
     * @param documentId 텍스트를 추출할 문서의 ID
     * @return 추출된 텍스트(String)
     * @throws DocumentNotFoundException  DB에 해당 문서 또는 원본 PDF 파일 정보가 없을 경우 (그대로 전파됨)
     * @throws StorageDownloadException   스토리지에서 PDF 파일을 다운로드하는 데 실패할 경우 (그대로 전파됨)
     * @throws PdfParseException          PDF 처리 중 오류가 발생할 경우
     */
    public String extractText(Long documentId) {
        // InputStream을 byte 배열로 읽어들인 후 Loader.loadPDF()에 전달하는 방식으로 처리합니다.
        // 이 방식이 PDFBox 3.x에서 스트림을 처리하는 표준적인 방법 중 하나입니다.

        try (InputStream inputStream = documentDownloadService.downloadOriginalPdf(documentId)) {
            try (PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) { // Loader.loadPDF() 사용
                PDFTextStripper stripper = new PDFTextStripper();
                String text = stripper.getText(document);

                // OCR 확장 포인트 (이전과 동일)
                // if (text.isBlank() || text.length() < SOME_THRESHOLD) { ... }

                return text;
            }
        } catch (IOException e) {
            // 파일 로드, 텍스트 추출 등 모든 I/O 관련 오류를 처리합니다.
            throw new PdfParseException("Failed to process PDF file for documentId: " + documentId, e);
        }
    }
}
