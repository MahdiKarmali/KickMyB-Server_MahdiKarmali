package org.kickmyb.server.task;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MTaskRepository extends JpaRepository<MTask, Long> {

    void deleteById(Long id);

}
