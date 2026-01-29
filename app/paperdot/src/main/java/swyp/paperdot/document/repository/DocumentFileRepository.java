package swyp.paperdot.document.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import swyp.paperdot.document.domain.DocumentFile;

public interface DocumentFileRepository extends JpaRepository<DocumentFile, Long> {

}
