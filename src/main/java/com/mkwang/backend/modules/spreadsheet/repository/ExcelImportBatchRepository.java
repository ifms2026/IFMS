package com.mkwang.backend.modules.spreadsheet.repository;
import com.mkwang.backend.modules.spreadsheet.entity.ExcelImportBatch;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface ExcelImportBatchRepository extends JpaRepository<ExcelImportBatch,String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from ExcelImportBatch b where b.id=:id and b.actorId=:actor")
    Optional<ExcelImportBatch> findLocked(@Param("id") String id,@Param("actor") Long actor);
    Optional<ExcelImportBatch> findByActorIdAndDatasetAndScopeKeyAndFileHashAndSchemaVersion(
            Long actorId,String dataset,String scopeKey,String fileHash,Integer schemaVersion);
}
