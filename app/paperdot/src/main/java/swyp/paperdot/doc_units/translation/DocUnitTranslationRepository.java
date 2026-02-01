package swyp.paperdot.doc_units.translation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * DocUnitTranslation 엔티티에 대한 데이터베이스 작업을 처리하는 리포지토리입니다.
 */
@Repository
public interface DocUnitTranslationRepository extends JpaRepository<DocUnitTranslation, Long> {
}
