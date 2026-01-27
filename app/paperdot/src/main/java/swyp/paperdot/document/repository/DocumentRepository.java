package swyp.paperdot.document.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import swyp.paperdot.document.domain.Document;

public interface DocumentRepository extends JpaRepository<Document, Long> {
}
