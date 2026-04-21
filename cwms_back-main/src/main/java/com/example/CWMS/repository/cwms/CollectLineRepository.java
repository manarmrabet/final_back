package com.example.CWMS.repository.cwms;

import com.example.CWMS.model.cwms.CollectLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface CollectLineRepository extends JpaRepository<CollectLine, Long> {

    List<CollectLine> findBySessionId(Long sessionId);



    int countBySessionId(Long sessionId);


}